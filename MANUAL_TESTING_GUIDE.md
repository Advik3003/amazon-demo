# Manual Testing Guide

## Authentication Testing

### 1. Register a New User
```
POST http://localhost:8080/api/v1/auth/register
Body:
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@test.com",
  "password": "Test@1234"
}
Expected: 201 Created with accessToken and refreshToken
```

### 2. Login
```
POST http://localhost:8080/api/v1/auth/login
Body:
{
  "email": "john@test.com",
  "password": "Test@1234"
}
Expected: 200 OK with tokens
```

### 3. Test Token Refresh
```
POST http://localhost:8080/api/v1/auth/refresh
Body: { "refreshToken": "<your_refresh_token>" }
Expected: 200 OK with new tokens (old refresh token is invalidated)
```

### 4. Test Logout
```
POST http://localhost:8080/api/v1/auth/logout
Authorization: Bearer <access_token>
Body: { "refreshToken": "<refresh_token>" }
Expected: 200 OK

Then try to use the old access token - should get 401
```

## Product Testing

### 5. Create a Product (needs auth)
```
POST http://localhost:8080/api/v1/products
Authorization: Bearer <token>
Body:
{
  "name": "iPhone 15 Pro",
  "description": "Latest Apple smartphone",
  "price": 999.99,
  "originalPrice": 1099.99,
  "brand": "Apple"
}
Expected: 201 Created
```

### 6. Get All Products
```
GET http://localhost:8080/api/v1/products?page=0&size=10&sortBy=price&sortDir=asc
Expected: 200 with paginated product list
```

### 7. Search Products
```
GET http://localhost:8080/api/v1/products/search?q=iphone
Expected: 200 with matching products
```

## Order Testing

### 8. Place an Order
```
POST http://localhost:8080/api/v1/orders
Authorization: Bearer <token>
Body:
{
  "items": [{"productId": "<product_id>", "quantity": 2}],
  "shippingFullName": "John Doe",
  "shippingStreet": "123 Main St",
  "shippingCity": "New York",
  "shippingState": "NY",
  "shippingZipCode": "10001",
  "shippingCountry": "US"
}
Expected: 201 Created with order details
```

### 9. Get My Orders
```
GET http://localhost:8080/api/v1/orders
Authorization: Bearer <token>
Expected: 200 with paginated order list
```

### 10. Cancel Order
```
PATCH http://localhost:8080/api/v1/orders/{orderId}/cancel
Authorization: Bearer <token>
Body: { "reason": "Changed my mind" }
Expected: 200 OK (only works for PENDING or CONFIRMED orders)
```

## Inventory Testing

### 11. Check Stock
```
GET http://localhost:8080/api/v1/inventory/check?productId=<id>&quantity=5
Expected: { "productId": "...", "availableQuantity": 100, "inStock": true }
```

## Verification Checklist

- [ ] Auth: Register works
- [ ] Auth: Login works
- [ ] Auth: Token refresh works
- [ ] Auth: Logout and token invalidation works
- [ ] Products: Create product visible in MongoDB (after Kafka sync)
- [ ] Products: Search works
- [ ] Orders: Place order works
- [ ] Orders: Cancel order works
- [ ] Notifications: Email arrives in Mailhog (http://localhost:8025)
- [ ] Kafka: Events visible in Kafka UI (http://localhost:8090)
- [ ] Eureka: All services registered (http://localhost:8761)
- [ ] Swagger: API docs load (http://localhost:8080/swagger-ui.html)
