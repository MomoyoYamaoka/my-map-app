# Safety Map App (HerRoute)

犯罪スコア等にもとづいて、地図上の道路を色分け表示するアプリです。

## 構成

- `frontend/`: React（Create React App）で地図UIを表示
  - バックエンド `GET /api/streets` から道路データを取得してポリライン描画
- `backend/`: Spring Boot（Overpass API + 犯罪スコアCSV）
  - Overpass API から道路形状を取得し、`CrimeScore.csv` をもとに道路ごとの平均スコアを計算
- `analyzer/`: Python（Street View + YOLO/ResNet等）でスコア算出用の分析処理（任意）
- `data/`: raw/interim/processed のデータ置き場（現状は空）

## 必要なもの

- Node.js（`frontend/` 用）
- Java 17（`backend/` の Spring Boot 用）
- （任意）Python（`analyzer/` 用）

## セットアップ（フロント）

Google Maps APIキーは **コードに直書きせず環境変数で指定**します。

1) `frontend/.env.example` をコピーして `frontend/.env` を作成

2) `VITE_GOOGLE_MAPS_API_KEY` を設定

（任意）バックエンドURLを変える場合は `VITE_API_BASE_URL` も設定

## 起動手順（開発）

### バックエンド

```bash
cd backend
./mvnw spring-boot:run
```

起動すると `http://localhost:8080/api/streets` が使えます。

### フロントエンド

```bash
cd frontend
npm install
npm run dev
```

`http://localhost:3000` を開きます。

※ もし `npm install` で `EPERM ... .npm` の権限エラーが出る場合は、次で回避できます。

```bash
cd frontend
npm install --cache .npm-cache
```

## データ（犯罪スコアCSV）

バックエンドは classpath 上の CSV を読み込みます。

- 優先: `backend/src/main/resources/data/StreetViewScore.csv`
  - Google Street View 解析（`analyzer/yolo/safety_sensor.py`）の結果CSVをここに配置し、
    ヘッダー `latitude, longitude, normalized_discomfort, ...` を持つ形式を想定しています。
  - `normalized_discomfort`（なければ `overall_discomfort`）をスコアとして道路の色分けに使用します。
- フォールバック: `backend/src/main/resources/data/CrimeScore.csv`
  - 形式: `latitude,longitude,score`

## analyzer（任意）

`analyzer/yolo/safety_sensor.py` は `GOOGLE_MAPS_API_KEY` を `.env` から読み込みます。
必要なら `analyzer/.env.example` を `analyzer/.env` にコピーして設定してください。
