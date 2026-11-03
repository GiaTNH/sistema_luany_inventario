# Seguridad: Prevención de Inyección SQL

## ¿Qué es la Inyección SQL y por qué es un riesgo?
La inyección SQL es una vulnerabilidad de seguridad que permite a un atacante interferir en las consultas que la aplicación realiza a su base de datos. Si no se sanitizan los datos de entrada, se podría manipular la lógica SQL para extraer información confidencial o alterar registros.

## Prevención mediante PreparedStatements y Spring Data JPA
Para mitigar este riesgo, el sistema implementa **Spring Data JPA** y consultas parametrizadas (**PreparedStatements**). Esto garantiza que los valores ingresados por los usuarios sean tratados estrictamente como datos y nunca como código ejecutable.

### Ejemplo de implementación segura en Repositorios:
```java
@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    // Consulta parametrizada segura que evita la inyección SQL
    @Query("SELECT p FROM Producto p WHERE p.nombre LIKE %:filtro% OR p.proveedor.nombre LIKE %:filtro%")
    List<Producto> buscarPorFiltroSeguro(@Param("filtro") String filtro);
}
