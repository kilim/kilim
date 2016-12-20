kilim-maven-plugin
==================

Kilim weaver plugin for maven


How to use it
=============

in your pom.xml

      ............

      <dependencies>
          <dependency>
              <groupId>org.db4j</groupId>
              <artifactId>kilim</artifactId>
              <version>2.0.0-2</version>
          </dependency>
      </dependencies>

      <build>
          <plugins>
              <plugin>
                  <groupId>org.db4j</groupId>
                  <artifactId>kilim-maven-plugin</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <executions>
                      <execution>
                          <id>weave</id>
                          <phase>process-classes</phase>
                          <goals> <goal>weave</goal> </goals>
                      </execution>
                  </executions>
              </plugin>
          </plugins>
      </build>

      ............
