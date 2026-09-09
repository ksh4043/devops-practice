# DevOps 파이프라인을 이해하기 위한 토이프로젝트 입니다.

스프링부트 앱과 MySQL 서버를 Docker 이미지로 빌드하고 GitHub Actions와 CI/CD 설정으로 배포 테스트 자동화 구축을 연습합니다.

## Day 1
### 앱 + Docker 기반 만들기
스프링부트로 작은 앱을 만들고 Dockerfile로 이미지를 만듭니다.

#### 스프링부트 앱 만들기
```bash
curl https://start.spring.io/starter.zip -d dependencies=web,actuator -d javaVersion=21 -d type=gradle-project -d name=devops-app -d packageName=com.example.devops -o app.zip

unzip app.zip
```
curl 커맨드로 스프링부트에서 제공하는 기본 앱을 받아 압축을 풉니다.

#### 인사말 페이지 추가하기
```bash
code src/main/java/com/example/devops/HelloController.java
```
앱을 구동했을 때 보여질 첫 페이지를 간단하게 만듭니다.

```Java
package com.example.devops;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/")
    public String hello() {
        return "Hello DevOps! 앱이 살아있습니다.";
    }
}
```

#### 앱 구동
```bash
./gradlew bootRun
```
앱을 실제로 구동하여 확인

#### Dockerfile 만들기
```bash
code Dockerfile
```
Dockerfile은 프로젝트의 루트 디렉토리에 위치해야 한다.
Dockerfile에는 앱을 빌드하기 위해 필요한 환경을 만드는 레시피라고 생각하면 된다.
어떤 기반 환경에서 무엇을 복사해서 넣고 어떻게 빌드하여 어떻게 실행할지를 적는 것.

```Dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```
temurin 오픈 소스 jdk를 이용했다.
이 환경의 이름을 build로 지정하였고 Docker 컨테이너에 담을 디렉토리의 이름을 app으로 지정한다.
COPY . . 의 의미는 Docker의 app 디렉토리에 로컬에 있는 모든 프로젝트를 복사하여 넣는다는 의미이다.
이후 실행 권한을 부여하고 빌드를 실행한다.

개발환경과 별도로 실행환경도 지정해주어야 한다.
COPY 라인에서 보이는 build는 위에서 지정한 build 개발 환경이다.
ENTRYPOINT는 컨테이너가 시작할 때 실행되는 명령이다.
RUN과의 차이점은 RUN은 컨테이너를 만들기 위한 명령어라고 생각하면 되고, ENTRYPOINT는 컨테이너가 이미지로 실행될 때라고 생각하면 될 것이다.

#### .dockerignore 만들기
```bash
code .dockerignore
```
Docker 역시 로컬의 빌드 찌꺼기나 캐시 등의 불필요한 파일을 제외하기 위해 ignore 설정을 한다.
이 프로젝트의 경우 아래와 같이 설정하였다.

```
.git
.gitignore
build
.gradle
Dockerfile
.dockerignore
README.md
HELP.md
```

#### Docker 명령어로 컨테이너 만들기
```bash
docker build -t devops-app .
```
이 명령어를 요약하면 "devops-app라는 태그(이름표)를 붙인 앱을 빌드하는데 필요한 재료의 위치는 현재 디렉토리(.)다" 라는 의미이다.

#### Docker 실행하기
```bash
docker run -p 8080:8080 devops-app
```
port 번호 설정은 -p 바깥포트:안쪽포트이다.