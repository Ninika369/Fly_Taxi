# FlyTaxi Ride-Hailing Platform

FlyTaxi is a scalable ride-hailing platform designed to connect passengers, drivers, and administrators. Built with modern technologies like **microservices architecture**, **distributed systems**, and **real-time data processing**, this platform ensures high performance, reliability, and scalability.

## Table of Contents
- [Overview](#overview)
- [Features](#features)
- [System Architecture](#system-architecture)
- [Modules](#modules)
- [Installation](#installation)
- [Usage](#usage)
- [API Documentation](#api-documentation)
- [Contributing](#contributing)
- [Future Improvements](#future-improvements)

---

## Overview

FlyTaxi is a full-fledged ride-hailing system that includes services for passengers, drivers, and administrators. The project uses a **microservices architecture** to ensure scalability, with an emphasis on real-world functionalities like **ride estimation**, **real-time driver/passenger matching**, and **payment integration**. This project serves as both a technical showcase and a real-world application.

---

## Features

### Passenger Features:
- **Sign Up/Login**: Passengers can register and log in using their phone number and OTP (One-Time Password).
- **Price Estimation**: Estimate the price of a ride based on the start and end points.
- **Ride Ordering**: Once the price is estimated, passengers can place a booking for a ride.
- **Payment Integration**: Integrated with **Alipay** for payment processing.

### Driver Features:
- **Driver Sign Up/Login**: Drivers can sign up and log in via phone number and OTP.
- **Ride Matching**: Drivers receive orders based on proximity and availability.
- **Order Status**: Track ride statuses (New, Dispatched, Accepted, In Progress, Completed).
- **Payment Request**: Drivers can request payment once the ride is completed.

### Admin (BOSS) Features:
- **User Management**: Admin can manage passengers, blacklist users, and resolve disputes.
- **Driver Management**: Admin can register drivers, verify driver credentials, and assign vehicles.
- **Vehicle Management**: Admin can manage vehicle information and statuses.

---

## System Architecture

The project follows a **microservices architecture**, which is divided into the following layers:

1. **Frontend Layer**:
    - Passenger App
    - Driver App
    - Admin Web Portal
    - WeChat Mini Program

2. **API Gateway Layer**:
    - Load balancing and request routing.

3. **Business Logic Layer**:
    - The core services handling passenger, driver, and admin operations.

4. **Capability Services Layer**:
    - Includes services for authentication, payment integration, and order management.

5. **Data Storage Layer**:
    - A centralized database for storing user, ride, and system data.

6. **Logging and Monitoring**:
    - For tracking system errors, application logs, and performance metrics.

---

## Modules

1. **Passenger Service (`service-passenger-user`)**:
    - Manages passenger-related functionalities such as registration, booking, and payment.
    - **Tech Stack**: Spring Boot, MyBatis, Redis

2. **Driver Service (`service-driver-user`)**:
    - Handles driver-related operations like ride matching, acceptance, and status tracking.
    - **Tech Stack**: Spring Boot, MyBatis, Redis

3. **Order Service (`service-order`)**:
    - Manages the lifecycle of ride orders, from creation to completion.
    - **Tech Stack**: Spring Boot, MyBatis, Redis

4. **Payment Service (`service-payment`)**:
    - Integrates **Alipay** for processing payments.
    - **Tech Stack**: Spring Boot, Alipay SDK

5. **Map Service (`service-map`)**:
    - Integrates **Gaode Map (Amap)** for real-time route optimization and navigation.
    - **Tech Stack**: Spring Boot, Amap API

6. **Verification Code Service (`service-verificationCode`)**:
    - Handles OTP verification for user authentication.
    - **Tech Stack**: Spring Boot, Redis

7. **Payment Service (`service-pay`)**:
    - Integrates Alipay for secure and reliable payment processing.
    - **Tech Stack**: Spring Boot, Alipay SDK, REST API

8. **Pricing Service (`service-price`)**:
    - Provides dynamic pricing and fare prediction based on rules and distance calculations.
    - **Tech Stack**: Spring Boot, MyBatis, MySQL

9. **SSE Push Service (`service-sse-push`)**:
    - Handles real-time server-sent events for notifications and updates.
    - **Tech Stack**: Spring Boot, SSE (Server-Sent Events)

---

## Testing

Testing is a crucial part of the development process. The following tools were used for testing the application:

1. **ApiFox**:
    - Used for **unit testing individual requests** to ensure proper API functionality.

2. **JMeter**:
    - Used to test the application under **multi-threaded and high concurrency** conditions, ensuring stability and performance under load.

---

## Installation

1. **Clone the repository**:
   ```bash
   git clone https://github.com/your-username/FlyTaxi.git
   cd FlyTaxi
   ```
2. **Install dependencies**:  
   Use Maven to install project dependencies.
   ```bash
   mvn clean install
   ```
3. **Database Setup**:
   Set up a MySQL database and configure the database credentials in application.yml.

---
## Usage

Once the system is set up, you can start using the services through the following endpoints:

- **Passenger API** (`api-passenger`): Access the app at `http://localhost:8081` to make bookings, track rides, and make payments.
- **Verification Code Service** (`service-verificationCode`): Access the service at `http://localhost:8082` for OTP verification.
- **Passenger User Service** (`service-passenger-user`): Access the service at `http://localhost:8083` for passenger user management.
- **Price Service** (`service-price`): Access the service at `http://localhost:8084` for fare calculations.
- **Map Service** (`service-map`): Access the service at `http://localhost:8085` for route optimization.
- **Driver User Service** (`service-driver-user`): Access the service at `http://localhost:8086` for driver user management.
- **Admin API** (`api-boss`): Access the admin panel at `http://localhost:8087` to manage users, drivers, and vehicles.
- **Driver API** (`api-driver`): Access the API at `http://localhost:8088` to manage driver operations.
- **Order Service** (`service-order`): Access the service at `http://localhost:8089` for ride order management.
- **SSE Push Service** (`service-sse-push`): Access the service at `http://localhost:9000` for simulating the user front-end page.
- **Pay Service** (`service-pay`): Access the service at `http://localhost:9001` for finishing the payment of an order.

---

## API Documentation

The project exposes the following three APIs:

### **Passenger API (`api-passenger`)**
1. **Verification Code**:
    - **GET** `/verification-code`: Generate a verification code for a passenger.
    - **POST** `/verification-code_check`: Validate the verification code for a passenger.
2. **Order Management**:
    - **POST** `/order/add`: Create a new ride order.
    - **GET** `/order/cancel`: Cancel an existing order.
3. **Predict Price**:
    - **GET** `/price/predict`: Get an estimated price for a ride based on start and end points.



### **Driver API (`api-driver`)**
1. **Verification Code**:
    - **POST** `/verification-code`: Send a verification code to a driver.
    - **POST** `/verification-code-check`: Validate the verification code for a driver.
2. **Order Management**:
    - **POST** `/order/**`: Give the driver the ability to change the order status and cancel the order.
3. **Driver Location**:
    - **POST** `/point/upload`: Update the driver’s real-time location.
4. **Payment**:
    - **POST** `/pay/push-pay-info`: Allow the driver to initiate payment by providing order ID, price, and passenger ID.



### **Admin API (`api-boss`)**
1. **Driver and Vehicle Management**:
    - **POST** `/driver-car-binding-relationship/bind`: Bind a driver to a vehicle.
    - **POST** `/driver-car-binding-relationship/unbind`: Unbind a driver from a vehicle.
    - **POST** `/driver-user`: Add a new driver to the system.
    - **PUT** `/driver-user`: Update the driver info in the system.
    - **POST** `/car`: Add a new car to the system.

### **Notes:**
- Each endpoint supports JSON-based request and response formats.
- Authentication is required for most endpoints, managed via JWT.
- For additional details, refer to the inline documentation in the codebase or contact the development team.


---

## Contributing

Contributions are welcome! If you'd like to contribute, please fork the repository and submit a pull request with your changes.

To report issues, please open an issue in the **Issues** section of the repository.

---

## Future Improvements

- **Real-time ride tracking** using WebSockets or gRPC.
- **Dynamic pricing algorithm** for better fare estimation.
- **Driver reservation system** for advanced order scheduling.
- **Refined user experience** with mobile app enhancements.
