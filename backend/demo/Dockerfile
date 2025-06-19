# ベースイメージに Maven を含める
FROM maven:3.9.5-eclipse-temurin-17

# 作業ディレクトリを指定
WORKDIR /app

# プロジェクトのすべてのファイルをコピー
COPY . .

# Jar をビルド（テストをスキップ）
RUN mvn clean package -DskipTests

# jar ファイルをコピーしてアプリ実行
FROM eclipse-temurin:17-jdk
WORKDIR /
COPY --from=0 /app/target/demo-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
