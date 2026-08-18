@echo off
java @jvm.options -jar target\mes-server-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
