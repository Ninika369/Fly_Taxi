# FlyTaxi

FlyTaxi is a scalable and modular ride-hailing platform designed to connect passengers, drivers, and administrators. It uses a microservices architecture to deliver robust and efficient service functionalities.

## Table of Contents

- [Introduction](#introduction)
- [Features](#features)
- [System Architecture](#system-architecture)
- [Modules](#modules)
    - [Passenger Service](#passenger-service)
    - [Driver Service](#driver-service)
    - [Order Service](#order-service)
    - [Map Service](#map-service)
    - [Payment Service](#payment-service)
    - [Verification Code Service](#verification-code-service)
    - [Admin API](#admin-api)
- [Installation](#installation)
- [Usage](#usage)
- [API Documentation](#api-documentation)
- [Future Enhancements](#future-enhancements)

---

## Introduction

FlyTaxi is a complete ride-hailing solution that offers features for passengers, drivers, and administrators. It is designed with a focus on scalability, ease of integration, and an optimized user experience.

---

## Features

### Passenger Platform
- Registration and login via OTP
- Price estimation for rides
- Ride booking and payment integration
- Feedback and ratings for drivers

### Driver Platform
- Driver login and profile management
- Real-time ride assignment
- Order lifecycle management
- Payment collection

### Admin Platform
- User and driver management
- Vehicle registration and status tracking
- System monitoring and dispute resolution

---

## System Architecture

The platform consists of multiple microservices, each handling a specific domain of the system. Key layers include:

1. **Frontend Layer**:
    - Passenger App
    - Driver App
    - Admin Dashboard
2. **API Gateway Layer**:
    - Centralized routing and load balancing
3. **Business Logic Layer**:
    - Core services for passengers, drivers, and admins
4. **Shared Services**:
    - Payment integration, user authentication, and order management
5. **Data Storage**:
    - Centralized databases for user and order information
6. **Logging and Monitoring**:
    - Error tracking and performance logging

---

## Modules

### Passenger Service
- **Module**: `service-passenger-user`
- **Key File**: `ServicePassengerUserApplication.java`
- **Description**: Handles passenger-side operations such as registration, booking, and ride management.
- **Technologies**: Spring Boot, MyBatis, Redis

### Driver Service
- **Module**: `service-driver-user`
- **Description**: Manages driver-related operations like ride assignments and order tracking.

### Order Service
- **Module**: `service-order`
- **Description**: Handles order lifecycle, from creation to completion, and ensures driver-passenger pairing.

### Map Service
- **Module**: `service-map`
- **Description**: Integrates Gaode Map (Amap) for real-time navigation and route optimization.

### Payment Service
- **Module**: `service-pay`
- **Description**: Manages payment transactions via Alipay and WeChat Pay.

### Verification Code Service
- **Module**: `service-verificationCode`
- **Description**: Provides OTP verification for user authentication.

### Admin API
- **Module**: `api-boss`
- **Description**: Backend API for managing users, drivers, and vehicles.

---

## Installation

1. **Clone the repository**:
   ```bash
   git clone https://github.com/your-repo/FlyTaxi.git
   cd FlyTaxi
