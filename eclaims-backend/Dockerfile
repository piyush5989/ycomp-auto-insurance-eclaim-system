FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

COPY mvnw .
COPY .mvn/ .mvn/
RUN chmod +x mvnw

COPY pom.xml .
COPY shared/kernel/pom.xml      shared/kernel/
COPY shared/contracts/pom.xml   shared/contracts/
COPY modules/claims/pom.xml     modules/claims/
COPY modules/customers/pom.xml  modules/customers/
COPY modules/documents/pom.xml  modules/documents/
COPY modules/workflow/pom.xml   modules/workflow/
COPY modules/notifications/pom.xml modules/notifications/
COPY modules/workshops/pom.xml  modules/workshops/
COPY modules/payments/pom.xml   modules/payments/
COPY modules/reporting/pom.xml  modules/reporting/
COPY modules/rentals/pom.xml    modules/rentals/
COPY app/eclaims-api/pom.xml    app/eclaims-api/

RUN ./mvnw dependency:go-offline -pl app/eclaims-api -am -B -q

COPY shared/   shared/
COPY modules/  modules/
COPY app/      app/
RUN ./mvnw -pl app/eclaims-api -am package -DskipTests -B -q

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S eclaims && adduser -S eclaims -G eclaims
WORKDIR /app
COPY --from=build /build/app/eclaims-api/target/eclaims-api-*.jar app.jar
RUN mkdir -p uploads && chown -R eclaims:eclaims /app
USER eclaims
EXPOSE 8090
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
