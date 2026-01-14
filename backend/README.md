# eCommerce Backend Application

A complete Spring Boot backend application for an eCommerce platform with authentication, product management, and admin features.

## Features

- **User Authentication**: JWT-based authentication with role-based access control (USER/ADMIN)
- **Product Management**: CRUD operations for products with search, filtering, and pagination
- **Admin Dashboard**: Analytics and user management endpoints
- **Security**: BCrypt password encoding, JWT token validation, CORS configuration
- **Database**: MySQL with JPA/Hibernate ORM
- **Validation**: Input validation using Bean Validation API

## Tech Stack

- Java 17
- Spring Boot 3.2.0
- Spring Security
- Spring Data JPA
- MySQL
- JWT (JSON Web Tokens)
- Lombok
- Maven

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- MySQL 8.0+

## Setup Instructions

### 1. Clone the repository
```bash
git clone <repository-url>
cd backend
```

### 2. Configure Database
Create a MySQL database:
```sql
CREATE DATABASE ecommerce_db;
```

### 3. Configure Environment Variables
Copy `.env.example` to `.env` and update the values:
```bash
cp .env.example .env
```

Edit `.env` with your database credentials and a secure JWT secret:
```properties
DB_URL=jdbc:mysql://localhost:3306/ecommerce_db?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC
DB_USERNAME=root
DB_PASSWORD=your_password

# Generate a secure JWT secret (minimum 64 characters)
# You can use: openssl rand -base64 64
JWT_SECRET=your_secure_random_jwt_secret_at_least_64_characters_long
```

**Important Security Note:** The JWT secret must be at least 64 characters long and should be a cryptographically random string. Never use predictable values in production.

Or update `src/main/resources/application.properties` directly.

### 4. Build the Project
```bash
mvn clean install
```

### 5. Run the Application
```bash
mvn spring-boot:run
```

Or run with a specific profile:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The application will start on `http://localhost:8080`

## API Endpoints

### Authentication Endpoints
- `POST /api/auth/register` - Register a new user
- `POST /api/auth/login` - Login user
- `GET /api/auth/me` - Get current user details (requires authentication)

### Product Endpoints
- `GET /api/products` - Get all products (with pagination, search, filtering)
- `GET /api/products/{id}` - Get product by ID
- `POST /api/products` - Create product (ADMIN only)
- `PUT /api/products/{id}` - Update product (ADMIN only)
- `DELETE /api/products/{id}` - Delete product (ADMIN only)

### Admin Endpoints
- `GET /api/admin/analytics` - Get analytics dashboard data (ADMIN only)
- `GET /api/admin/users` - Get all users (ADMIN only)

## API Request Examples

### Register User
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "email": "john@example.com",
    "password": "password123"
  }'
```

### Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "password": "password123"
  }'
```

### Get Products
```bash
curl -X GET "http://localhost:8080/api/products?page=0&size=10"
```

### Create Product (Admin)
```bash
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "name": "Laptop",
    "description": "High-performance laptop",
    "price": 999.99,
    "category": "Electronics",
    "stock": 50,
    "imageUrl": "http://example.com/laptop.jpg"
  }'
```

## Database Schema

### Users Table
- id (Long, Primary Key)
- name (String)
- email (String, Unique)
- password (String, Encrypted)
- role (Enum: USER/ADMIN)
- created_at (Timestamp)
- updated_at (Timestamp)

### Products Table
- id (Long, Primary Key)
- name (String)
- description (Text)
- price (Decimal)
- category (String)
- stock (Integer)
- image_url (String)
- created_at (Timestamp)
- updated_at (Timestamp)

### Orders Table
- id (Long, Primary Key)
- user_id (Long)
- total_amount (Decimal)
- status (Enum: PENDING/COMPLETED/CANCELLED)
- created_at (Timestamp)

## Security

- Passwords are encrypted using BCrypt
- JWT tokens are used for authentication
- Role-based access control (USER/ADMIN)
- CORS enabled for frontend at `http://localhost:3000` and `http://localhost:5173`

## Testing

Run tests with:
```bash
mvn test
```

## Project Structure

```
backend/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── buyogo/
│   │   │           └── ecommerce/
│   │   │               ├── config/          # Configuration classes
│   │   │               ├── controller/      # REST controllers
│   │   │               ├── dto/             # Data Transfer Objects
│   │   │               ├── entity/          # JPA entities
│   │   │               ├── exception/       # Exception handlers
│   │   │               ├── repository/      # JPA repositories
│   │   │               ├── security/        # Security components
│   │   │               ├── service/         # Business logic
│   │   │               └── EcommerceApplication.java
│   │   └── resources/
│   │       ├── application.properties
│   │       └── application-dev.properties
│   └── test/
│       └── java/
├── pom.xml
└── .env.example
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License.
