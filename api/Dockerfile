FROM openjdk
EXPOSE 9522
VOLUME /tmp
#ADD api.jar /app.jar
ADD api/target/api.jar /app.jar
RUN bash -c 'touch /app.jar'
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-Duser.timezone=GMT+8","-jar","/app.jar"]
