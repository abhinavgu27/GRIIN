# 🌍 GRIIN: AI-Powered Road Hazard Detection

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![Render](https://img.shields.io/badge/Deployed_on-Render-46E3B7?style=for-the-badge&logo=render&logoColor=white)

**GRIIN** is a full-stack mobile application and cloud backend designed to autonomously detect, map, and log road hazards (such as potholes and speed bumps) in real-time. Built to improve municipal infrastructure monitoring, the system utilizes on-device machine learning paired with geospatial database mapping.

## ✨ Key Features
* **Real-Time Edge AI:** Processes live camera feeds on-device to detect road hazards with up to **85% confidence**, minimizing server latency.
* **Geospatial Logging:** Automatically captures and syncs the exact GPS coordinates of detected hazards.
* **Cloud-Native Backend:** RESTful API built with Spring Boot, containerized via Docker, and deployed on Render.
* **Spatial Database:** Utilizes PostgreSQL with the **PostGIS** extension to store and query spatial hazard data securely on Neon.

## 🏗️ System Architecture
1. **Frontend (Android/Kotlin):** Captures video, runs the custom AI detection model, and extracts GPS coordinates.
2. **Network Layer (Retrofit):** Transmits JSON payload containing hazard type, confidence score, and location via `POST /api/v1/hazards`.
3. **Backend (Spring Boot 3):** Validates incoming requests, processes spatial data using Hibernate Spatial, and manages database connections via HikariCP.
4. **Database (Neon PostgreSQL):** Stores hazard records using PostGIS geometry types for future map plotting and analysis.

## 💻 Tech Stack
* **Mobile Frontend:** Kotlin, MVVM Architecture, Retrofit, CameraX, Custom ML Model.
* **Backend API:** Java 21, Spring Boot 3, Spring Data JPA, Hibernate Spatial, Gradle.
* **Database & DevOps:** PostgreSQL (Neon), PostGIS, Docker, Git, Render Cloud Hosting.

## 🚀 Local Development Setup
1. Clone the repository**

2. Backend Setup (/griin-server)

Ensure Java 21 is installed.

Provide your Neon PostgreSQL credentials via environment variables (SPRING_DATASOURCE_URL, USERNAME, PASSWORD).

Run the server: ./gradlew bootRun

3. Frontend Setup (/app)

Open the project in Android Studio.

Update BASE_URL in RetrofitClient.kt to point to your local machine's IP address.

Build and deploy to a physical Android device for camera and AI testing.

©️ License
This project is proprietary and confidential. Unauthorized copying, modification, distribution, or use of this software, via any medium, is strictly prohibited without the express written permission of the author.
