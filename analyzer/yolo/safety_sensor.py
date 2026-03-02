import os
import shutil
import requests
from PIL import Image
import io
from dotenv import load_dotenv
from typing import Dict, Tuple, List
import torch
import torchvision.transforms as transforms
from torchvision.models import resnet50, ResNet50_Weights
import numpy as np
import cv2
from ultralytics import YOLO
import pandas as pd
from datetime import datetime

def create_seattle_grid(step_size: float = 0.005) -> List[Tuple[float, float]]:
    """
    シアトル全域のグリッドポイントを生成
    step_size: 緯度・経度の間隔（度）
    """
    # シアトルの概ねの範囲
    lat_min, lat_max = 47.5, 47.7  # 北緯
    lon_min, lon_max = -122.4, -122.3  # 東経
    
    locations = []
    lat = lat_min
    while lat <= lat_max:
        lon = lon_min
        while lon <= lon_max:
            locations.append((lat, lon))
            lon += step_size
        lat += step_size
    
    return locations

def normalize_scores(scores: List[float]) -> List[float]:
    """
    Min-Max正規化を行う
    """
    min_score = min(scores)
    max_score = max(scores)
    if max_score == min_score:
        return [0.5] * len(scores)  # すべて同じ値の場合は0.5を返す
    
    return [(score - min_score) / (max_score - min_score) for score in scores]

class SafetySensor:
    def __init__(self):
        load_dotenv()
        self.api_key = os.getenv("GOOGLE_MAPS_API_KEY")
        if not self.api_key:
            raise RuntimeError("Missing GOOGLE_MAPS_API_KEY in environment (.env).")
        self.base_url = "https://maps.googleapis.com/maps/api/streetview"
        
        # モデルの初期化
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.model = resnet50(weights=ResNet50_Weights.DEFAULT)
        self.model = self.model.to(self.device)
        self.model.eval()
        
        # YOLOモデルの初期化
        self.yolo_model = YOLO('yolov8n.pt')
        
        # 画像の前処理
        self.transform = transforms.Compose([
            transforms.Resize(256),
            transforms.CenterCrop(224),
            transforms.ToTensor(),
            transforms.Normalize(
                mean=[0.485, 0.456, 0.406],
                std=[0.229, 0.224, 0.225]
            )
        ])
        
        # 出力先: outputs/ にするとバックエンドが ../analyzer/yolo/outputs から参照できる
        self.output_dir = "outputs"
        self.images_dir = os.path.join(self.output_dir, "images")
        os.makedirs(self.output_dir, exist_ok=True)
        os.makedirs(self.images_dir, exist_ok=True)

    def get_street_view_image(self, location: Tuple[float, float], heading: int = 0) -> Image.Image:
        params = {
            'size': '640x640',
            'location': f"{location[0]},{location[1]}",
            'heading': heading,
            'pitch': 0,
            'fov': 90,
            'key': self.api_key
        }
        try:
            response = requests.get(self.base_url, params=params)
            response.raise_for_status()
            return Image.open(io.BytesIO(response.content))
        except Exception as e:
            print(f"Error fetching street view image: {e}")
            return None

    def preprocess_image(self, image: Image.Image) -> torch.Tensor:
        """画像の前処理を行う"""
        return self.transform(image).unsqueeze(0).to(self.device)

    def analyze_image(self, image: Image.Image) -> Dict[str, float]:
        """画像を分析して不快感の要素を評価"""
        # 画像を前処理
        input_tensor = self.preprocess_image(image)
        
        # モデルで特徴抽出
        with torch.no_grad():
            features = self.model(input_tensor)
        
        # 画像をOpenCV形式に変換
        cv_image = cv2.cvtColor(np.array(image), cv2.COLOR_RGB2BGR)
        
        # YOLOで物体検出
        results = self.yolo_model(cv_image)
        
        # 各要素の分析
        decay_score = self.analyze_decay(cv_image, results)
        human_score = self.analyze_human_presence(results)
        visibility_score = self.analyze_visibility(cv_image)
        lighting_score = self.analyze_lighting(cv_image)
        maintenance_score = self.analyze_maintenance(cv_image)
        
        return {
            "decay": decay_score,
            "human_presence": human_score,
            "visibility": visibility_score,
            "lighting": lighting_score,
            "maintenance": maintenance_score
        }

    def analyze_decay(self, image: np.ndarray, yolo_results) -> float:
        """荒廃度（落書き・ごみ）の分析"""
        # YOLOの検出結果から荒廃に関連する物体をカウント
        decay_objects = ['bottle', 'cup', 'wine glass', 'bowl', 'vase', 'scissors', 'book', 'clock', 'vase']
        decay_count = sum(1 for r in yolo_results[0].boxes.data for cls in decay_objects 
                         if yolo_results[0].names[int(r[5])] == cls)
        
        # グレースケール変換
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        
        # エッジ検出
        edges = cv2.Canny(gray, 50, 150)
        
        # エッジの密度を計算
        edge_density = np.sum(edges) / (edges.shape[0] * edges.shape[1])
        
        # スコアを計算（物体検出とエッジ密度の組み合わせ）
        score = (edge_density * 5) + (decay_count * -1)
        return np.clip(score, -5, 5)

    def analyze_human_presence(self, yolo_results) -> float:
        """人の気配の分析"""
        # YOLOの検出結果から人物をカウント
        person_count = sum(1 for r in yolo_results[0].boxes.data 
                          if yolo_results[0].names[int(r[5])] == 'person')
        
        # スコアを計算（1人につき+1点、最大+5点）
        score = min(person_count, 5)
        return score

    def analyze_visibility(self, image: np.ndarray) -> float:
        """見通しの分析"""
        # グレースケール変換
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        
        # エッジ検出
        edges = cv2.Canny(gray, 50, 150)
        
        # 水平方向のエッジを強調
        horizontal_edges = cv2.Sobel(gray, cv2.CV_64F, 1, 0, ksize=3)
        
        # 水平方向のエッジの強度を計算
        visibility_score = np.mean(np.abs(horizontal_edges))
        
        # スコアを-5から+5の範囲に正規化
        score = (visibility_score / 50) - 5
        return np.clip(score, -5, 5)

    def analyze_lighting(self, image: np.ndarray) -> float:
        """照明状態の分析"""
        # グレースケール変換
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        
        # 平均輝度を計算
        brightness = np.mean(gray)
        
        # スコアを-5から+5の範囲に正規化
        score = (brightness / 25.5) - 5
        return np.clip(score, -5, 5)

    def analyze_maintenance(self, image: np.ndarray) -> float:
        """整備状態の分析"""
        # グレースケール変換
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        
        # ノイズの検出
        noise = cv2.Laplacian(gray, cv2.CV_64F).var()
        
        # スコアを-5から+5の範囲に正規化
        score = 5 - (noise / 1000)  # ノイズが少ないほど高スコア
        return np.clip(score, -5, 5)

    def calculate_overall_discomfort(self, factors: Dict[str, float]) -> float:
        weights = {
            "decay": 1.0,
            "human_presence": 1.0,
            "visibility": 1.0,
            "lighting": 0.5,
            "maintenance": 0.5
        }
        weighted_sum = sum(factors[key] * weights[key] for key in factors.keys())
        total_weight = sum(weights.values())
        normalized_score = weighted_sum / total_weight
        return normalized_score

    def save_image(self, image: Image.Image, location: Tuple[float, float], heading: int) -> str:
        """画像を保存し、ファイル名を返す"""
        lat, lon = location
        filename = f"{lat:.4f}_{lon:.4f}_{heading}.jpg"
        filepath = os.path.join(self.images_dir, filename)
        image.save(filepath)
        return filename

    def analyze_location(self, location: Tuple[float, float]) -> Dict:
        images = []
        image_files = []
        for heading in [0, 90, 180, 270]:
            image = self.get_street_view_image(location, heading)
            if image:
                images.append(image)
                image_files.append(self.save_image(image, location, heading))

        if not images:
            print("No images could be fetched for this location.")
            return {
                "location": location,
                "individual_factors": {},
                "overall_discomfort": None,
                "image_files": []
            }

        all_factors = []
        for image in images:
            factors = self.analyze_image(image)
            all_factors.append(factors)

        avg_factors = {
            key: sum(f[key] for f in all_factors) / len(all_factors)
            for key in all_factors[0].keys()
        }
        overall_score = self.calculate_overall_discomfort(avg_factors)
        return {
            "location": location,
            "individual_factors": avg_factors,
            "overall_discomfort": overall_score,
            "image_files": image_files
        }

def main():
    sensor = SafetySensor()
    
    # シアトル全域のグリッドポイントを生成
    locations = create_seattle_grid()
    print(f"評価地点数: {len(locations)}")
    
    # 結果を格納するリスト
    results = []
    overall_scores = []
    
    # 各地点の評価
    for i, coordinates in enumerate(locations, 1):
        print(f"\n地点 {i}/{len(locations)} の評価中: {coordinates}")
        
        result = sensor.analyze_location(coordinates)
        
        if result['overall_discomfort'] is not None:
            # 結果を辞書に格納
            result_dict = {
                "latitude": coordinates[0],
                "longitude": coordinates[1],
                "overall_discomfort": result['overall_discomfort'],
                "image_files": ",".join(result['image_files'])
            }
            
            # 個別要素の評価を追加
            for factor, score in result['individual_factors'].items():
                result_dict[factor] = score
            
            results.append(result_dict)
            overall_scores.append(result['overall_discomfort'])
            
            print(f"総合不快感スコア: {result['overall_discomfort']:.2f}")
    
    # スコアの正規化
    normalized_scores = normalize_scores(overall_scores)
    
    # 正規化されたスコアを結果に追加
    for result, norm_score in zip(results, normalized_scores):
        result['normalized_discomfort'] = norm_score
    
    # DataFrameの作成
    df = pd.DataFrame(results)
    
    # カラムの順序を整理
    columns = [
        "latitude", "longitude", "overall_discomfort", "normalized_discomfort",
        "decay", "human_presence", "visibility", "lighting", "maintenance",
        "image_files"
    ]
    df = df[columns]
    
    # CSVファイルの保存（Google Street View 画像から算出したスコア → バックエンドで地図色分けに利用）
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    csv_filename = f"seattle_analysis_{timestamp}.csv"
    csv_path = os.path.join(sensor.output_dir, csv_filename)
    df.to_csv(csv_path, index=False, encoding='utf-8')
    
    # バックエンドの data/ に StreetViewScore.csv としてコピー（優先読込用）
    _script_dir = os.path.dirname(os.path.abspath(__file__))
    backend_data_dir = os.path.join(_script_dir, "..", "..", "backend", "src", "main", "resources", "data")
    backend_data_dir = os.path.normpath(backend_data_dir)
    if os.path.isdir(backend_data_dir):
        dest = os.path.join(backend_data_dir, "StreetViewScore.csv")
        shutil.copy2(csv_path, dest)
        print(f"Backend用にコピーしました: {dest}")
    
    print(f"\n分析結果を保存しました:")
    print(f"CSVファイル: {csv_path}")
    print(f"画像ファイル: {sensor.images_dir}")
    
    # 基本統計量の表示
    print("\n基本統計量:")
    print(df[['overall_discomfort', 'normalized_discomfort']].describe())

if __name__ == "__main__":
    main()