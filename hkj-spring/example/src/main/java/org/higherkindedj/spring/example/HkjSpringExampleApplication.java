package org.higherkindedj.spring.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Example Spring Boot application demonstrating higher-kinded-j integration.
 * <p>
 * This application demonstrates:
 * <ul>
 *   <li>Automatic Either return value handling in REST controllers</li>
 *   <li>Type-safe error handling without exceptions</li>
 *   <li>Composable functional patterns in Spring</li>
 * </ul>
 * </p>
 * <p>
 * Try these endpoints:
 * <ul>
 *   <li>GET http://localhost:8080/api/users - Get all users</li>
 *   <li>GET http://localhost:8080/api/users/1 - Get user by ID (success)</li>
 *   <li>GET http://localhost:8080/api/users/999 - Get user by ID (404 error)</li>
 *   <li>POST http://localhost:8080/api/users - Create user (with validation)</li>
 * </ul>
 * </p>
 */
@SpringBootApplication
public class HkjSpringExampleApplication {

  public static void main(String[] args) {
    SpringApplication.run(HkjSpringExampleApplication.class, args);
  }
}
