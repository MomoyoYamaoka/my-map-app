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

### http://localhost:8080/ でアプリを確認する（推奨）

1. フロントをビルドしてバックエンドの静的フォルダにコピーする（初回またはフロントを変更したあと）:
   ```bash
   npm run build:backend
   ```
2. バックエンドを起動する:
   ```bash
   npm run backend:run
   ```
   または
   ```bash
   cd backend && ./mvnw spring-boot:run
   ```
3. ブラウザで **http://localhost:8080/** を開く。地図アプリが表示され、`/api/streets` も同じオリジンで呼ばれます。

### 別の方法: フロントのみ別ポートで動かす

**バックエンド**
```bash
cd backend
./mvnw spring-boot:run
```
→ `http://localhost:8080/api/streets` が使えます。

**フロントエンド**
```bash
cd frontend
npm install
npm run dev
```
→ `http://localhost:5173` を開きます（API は localhost:8080 を参照）。

※ もし `npm install` で `EPERM ... .npm` の権限エラーが出る場合は、次で回避できます。

```bash
cd frontend
npm install --cache .npm-cache
```

## データ（危険度スコア）と処理の流れ

**処理の流れ:**  
1. **Street View 画像を読む** → analyzer が Google Street View 画像から不快感・危険度を数値化した CSV を出力  
2. **警察の犯罪データを読む** → `CrimeScore.csv`（緯度・経度・スコア）を読む  
3. **二つのスコアを平均化** → 各道路について、近傍の Street View スコアの平均と犯罪スコアの平均を求め、その平均を新たな危険度スコアとする  
4. **地図表示** → そのスコアをパーセンタイルで色分けして Google 地図上に表示  

バックエンドが読む CSV:

- **Street View 由来**
  - `../analyzer/yolo/outputs/seattle_analysis_*.csv` の最新、または classpath: `backend/src/main/resources/data/StreetViewScore.csv`
  - ヘッダーに `latitude`, `longitude`, `normalized_discomfort`（なければ `overall_discomfort`）を含む形式
- **警察の犯罪データ**
  - `backend/src/main/resources/data/CrimeScore.csv`（形式: `latitude,longitude,score`）

道路ごとに「Street View 近傍平均」と「犯罪データ近傍平均」の両方がある場合はその平均を、片方だけある場合はその値を使い、どちらも無い道路は表示しません。

## analyzer（Street View 画像 → スコア作成）

`analyzer/yolo/safety_sensor.py` で **Google Street View の画像を取得し**、ResNet/YOLO 等で解析して不快感スコア（`overall_discomfort` / `normalized_discomfort`）を算出します。

1. `GOOGLE_MAPS_API_KEY` を `.env` に設定（`analyzer/.env.example` をコピーして `analyzer/.env` を作成）
2. 実行:
   ```bash
   cd analyzer/yolo
   pip install -r requirements.txt  # 未済なら
   python safety_sensor.py
   ```
3. 出力:
   - `analyzer/yolo/outputs/seattle_analysis_YYYYMMDD_HHMMSS.csv` に保存
   - 同じ内容を `backend/src/main/resources/data/StreetViewScore.csv` にコピー（バックエンドの優先読込用）

このあとバックエンドを起動すると、地図は Street View 由来スコアで色分けされます。

## デプロイ（Render）

このリポジトリは **Render** で Docker としてデプロイできます。フロント・バックエンドを1本のイメージにまとめて配信します。

### 前提

- コードを **GitHub / GitLab / Bitbucket** に push していること
- [Render](https://render.com) のアカウントがあること

### 手順

1. **Render にログイン**し、**New → Blueprint** でリポジトリを接続する（または **New → Web Service** でリポジトリを選ぶ）。

2. **Blueprint でデプロイする場合**
   - リポジトリ接続後、`render.yaml` を検出してサービスが作成される。
   - **Environment** で次の変数を追加する:
     - `VITE_GOOGLE_MAPS_API_KEY`: Google Maps / Street View 用 API キー（**Secret** 推奨・ビルド時にフロントに埋め込まれる）

3. **Web Service で手動設定する場合**
   - **Environment**: `Docker`
   - **Dockerfile Path**: `./Dockerfile`（リポジトリルート）
   - **Root Directory**: 空のまま（ルートでビルド）
   - **Environment Variables**: `VITE_GOOGLE_MAPS_API_KEY` を追加（値は Secret 推奨）

4. **Deploy** を実行する。ビルドが終わると、Render の URL（例: `https://herroute.onrender.com`）でアプリが開きます。

### ビルドの流れ（Dockerfile）

- **Stage 1**: Node で `frontend` をビルド（`VITE_GOOGLE_MAPS_API_KEY` を ARG で受け取り可能）
- **Stage 2**: Maven で `backend` をビルドし、フロントの `dist/` を `backend/.../static/` にコピー
- **Stage 3**: JAR だけのランタイムイメージで `java -jar` で起動

デプロイ後は同一オリジンで `/` に地図、`/api/streets` に API が提供されます。
