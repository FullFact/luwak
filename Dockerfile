FROM maven
RUN mkdir /code
# The first ADD is the one that determines cache invalidation
ADD ./pom.xml /code  
ADD . /code
WORKDIR /code
RUN mvn install
EXPOSE 8080 8081
CMD java -jar luwak-server/target/luwak-server-1.5.0-SNAPSHOT.jar