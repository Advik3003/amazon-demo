# Amazon Demo - Android Application

## Overview
Native Android application (Java) for the Amazon Demo e-commerce platform.

## Architecture
- **Language**: Java
- **Min SDK**: 26 (Android 8.0 Oreo)
- **Target SDK**: 34 (Android 14)
- **Architecture Pattern**: MVVM (Model-View-ViewModel)
- **Navigation**: Navigation Component with Bottom Navigation

## Key Features
- User Registration & Login (JWT-based)
- Product Browsing (Grid layout, pagination)
- Product Detail with images
- Cart Management
- Order History
- Theme Support

## Tech Stack
| Library | Purpose |
|---------|---------|
| Retrofit 2 | REST API calls |
| OkHttp 4 | HTTP client |
| Gson | JSON parsing |
| Glide | Image loading/caching |
| Material Design | UI components |
| Navigation Component | Fragment navigation |
| View Binding | Type-safe view access |
| LiveData + ViewModel | State management |

## How to Run
1. Install Android Studio (latest stable)
2. Open the `android/` folder in Android Studio
3. Wait for Gradle sync
4. Update `API_BASE_URL` in `app/build.gradle`:
   - Emulator: `http://10.0.2.2:8080`
   - Physical device: `http://YOUR_PC_IP:8080`
5. Run on emulator or device

## API Integration
All API calls go through `ApiClient.java` which uses Retrofit.
JWT token is automatically added to every request via OkHttp interceptor.

## Configuration
Change `API_BASE_URL` in `app/build.gradle` for different environments:
```gradle
buildConfigField "String", "API_BASE_URL", '"http://10.0.2.2:8080"'  // Emulator
buildConfigField "String", "API_BASE_URL", '"http://192.168.1.x:8080"'  // Physical device
buildConfigField "String", "API_BASE_URL", '"https://api.yourproduction.com"'  // Production
```

## App Structure
```
ui/
  auth/       - Login, Register activities
  home/       - Main activity with bottom navigation
  product/    - Product list, detail fragments
  cart/       - Cart fragment
  order/      - Order list, detail fragments
api/
  ApiClient   - Retrofit configuration + token management
  ApiService  - All API endpoint definitions
model/        - Data classes (DTOs)
utils/        - Utility classes
```
