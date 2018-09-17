kilim-maven-plugin
==================

the kilim maven artifact is also a maven plugin, ie it will weave your code ahead-of-time

the default phase is process tests, which is needed to weave both classes and test classes.
if you don't want to weave tests, or you're not running process tests, you'll need to override this,
eg, with process classes


How to use it
=============

in your pom.xml

      ............

      <dependencies>
          <dependency>
              <groupId>org.db4j</groupId>
              <artifactId>kilim</artifactId>
              <version>2.0.0-25</version>
          </dependency>
      </dependencies>

      <build>
          <plugins>
              <plugin>
                  <groupId>org.db4j</groupId>
                  <artifactId>kilim</artifactId>
                  <version>2.0.0-25</version>
                  <executions>
                      <execution>
                          <goals><goal>weave</goal></goals>
                      </execution>
                  </executions>
              </plugin>
          </plugins>
      </build>

      ............


afaik, the versions for the plugin must match the version for the dependency

