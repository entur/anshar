FROM openjdk:11-jdk
ADD target/anshar-*-SNAPSHOT.jar anshar.jar

EXPOSE 8776
CMD java $JAVA_OPTIONS -jar /anshar.jar