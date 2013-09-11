kilim-maven-plugin
==================

Kilim weaver plugin for maven


How to use it
=============

in your pom.xml

```
      ............

      <dependencies>
          <dependency>
              <groupId>kilim</groupId>
              <artifactId>kilim</artifactId>
              <version>1.0</version>
          </dependency>
      </dependencies>

      <build>
          <plugins>
              <plugin>
                  <groupId>io.kilimty</groupId>
                  <artifactId>kilim-maven-plugin</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <executions>
                      <execution>
                          <id>weave</id>
                          <phase>process-classes</phase>
                          <goals>
                              <goal>weave</goal>
                          </goals>
                      </execution>
                  </executions>
              </plugin>
          </plugins>
      </build>

      ............

```