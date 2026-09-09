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
Dockerfile은 프로젝트의 루트 디렉토리에 위치해야 합니다.
Dockerfile에는 앱을 빌드하기 위해 필요한 환경을 만드는 레시피라고 생각하면 됩니다.
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
temurin 오픈 소스 jdk를 이용했습니다.
이 환경의 이름을 build로 지정하였고 Docker 컨테이너에 담을 디렉토리의 이름을 app으로 지정합니다.
COPY . . 의 의미는 Docker의 app 디렉토리에 로컬에 있는 모든 프로젝트를 복사하여 넣는다는 의미입니다.
이후 실행 권한을 부여하고 빌드를 실행합니다.

개발환경과 별도로 실행환경도 지정해주어야 합니다.
COPY 라인에서 보이는 build는 위에서 지정한 build 개발 환경입니다.
ENTRYPOINT는 컨테이너가 시작할 때 실행되는 명령입니다.
RUN과의 차이점은 RUN은 컨테이너를 만들기 위한 명령어라고 생각하면 되고, ENTRYPOINT는 컨테이너가 이미지로 실행될 때라고 생각하면 됩니다.

#### .dockerignore 만들기
```bash
code .dockerignore
```
Docker 역시 로컬의 빌드 찌꺼기나 캐시 등의 불필요한 파일을 제외하기 위해 ignore 설정을 합니다.
이 프로젝트의 경우 아래와 같이 설정하였습니다.

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
이 명령어를 요약하면 "devops-app라는 태그(이름표)를 붙인 앱을 빌드하는데 필요한 재료의 위치는 현재 디렉토리(.)다" 라는 의미입니다.

#### Docker 실행하기
```bash
docker run -p 8080:8080 devops-app
```
port 번호 설정은 -p 바깥포트:안쪽포트입니다.

## Day 2
### MySQL과 JPA 의존성 주입
```gradle
	implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
	runtimeOnly 'com.mysql:mysql-connector-j'
```
위의 두 줄을 build.gradle에 추가합니다.

### 방문 횟수 저장하여 보여주기 + Docker Compose 설정
#### 방문 횟수 저장 Entity 생성
```bash
code src/main/java/com/example/devops/VisitCount.java
```

```Java
package com.example.devops;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class VisitCount {

    @Id
    private Long id;

    private Long count;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }
}
```

이후 빌드를 실행하면 되는데 만약 에러가 발생할 경우
```bash
./gradlew dependencies
```
명령어로 의존성 라이브러리를 미리 받아두기만 해도 됩니다.
현재 단계까지 프로젝트는 아직 완성되어 있지 않기 때문에 빌드 시 에러는 무시하고 라이브러리만 받아도 무관합니다.

#### 방문 횟수를 DB에 관리하는 도구 Repository 만들기

```bash
code src/main/java/com/example/devops/VisitCountRepository.java
```

```Java
package com.example.devops;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitCountRepository extends JpaRepository<VisitCount, Long> {
}
```

#### HelloController를 수정하여 방문 횟수 표시하기

```bash
code src/main/java/com/example/devops/HelloController.java
```

```Java
package com.example.devops;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {
    
    private final VisitCountRepository repository;

    public HelloController(VisitCountRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/")
    public String hello() {
        VisitCount visit = repository.findById(1L).orElseGet(() -> {
            VisitCount newVisit = new VisitCount();
            newVisit.setId(1L);
            newVisit.setCount(0L);
            return newVisit;
        });

        visit.setCount(visit.getCount() + 1);
        repository.save(visit);

        return "Hello DevOps! 방문 횟수: " + visit.getCount();
    }
}
```
#### compose 파일 설정하기
```bash
code compose.yaml
```

```yaml
services:
  db:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: rootpassword
      MYSQL_DATABASE: devops
    ports:
      - "3306:3306"
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-prootpassword"]
      interval: 5s
      timeout: 5s
      retries: 10

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://db:3306/devops
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: rootpassword
    depends_on:
      db:
        condition: service_healthy
```
compose.yaml은 Dockerfile로 만들어진 컨테이너를 어떻게 같이 띄우고 연결할지를 설정하는 파일입니다.
services는 최상위 항목으로 띄울 컨테이너(서비스)를 구분합니다.
db와 app은 각각 사용자 정의 이름으로 db는 MySQL, app은 스프링앱입니다.
이 이름들이 중요한 점은 app의 항목 중 depends_on에서 지정하는 db가 위에 적힌 db(이름)를 참조하기 때문입니다.

#### 변경 사항 적용하며 서버 띄우기
```bash
docker compose up --build
```
--build가 추가 된 이유는 그냥 띄우게 되면 변경 사항이 적용되지 않기 때문에 다시 한 번 빌드를 하고 서버를 띄우라는 의미입니다.

## Day 3
### GitHub Actions와 CI
GitHub Actions는 보통 개발자가 코드를 올릴 때(push) 정해둔 작업(빌드, 테스트)을 해주는 자동화 도구입니다.
GitHub Actions는 정해진 위치에 파일이 있어야 인식합니다. 프로젝트 안에 .github/workflows/ 라는 폴더를 만들고, 그 안에 워크플로 파일을 넣어야 합니다.
따라서, 아래 명령어를 따라 먼저 디렉토리와 파일을 만듭니다.

#### GitHub Actions를 위한 CI 설정
```bash
mkdir -p .github/workflows
code .github/workflows/ci.yml
```

이제 만든 ci.yml 파일에 GitHub Actions에 할 일을 지정하겠습니다.
이 프로젝트에서는 main 브랜치에 push 했을 때와 pull request 요청이 생겨도 실행하게 할 것이고, MySQL 서버와 스프링 앱 빌드 테스트를 하도록 작성하겠습니다.

```yml
name: CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest

    services:
      mysql:
        image: mysql:8.0
        env:
          MYSQL_ROOT_PASSWORD: rootpassword
          MYSQL_DATABASE: devops
        ports:
          - 3306:3306
        options: >-
          --health-cmd="mysqladmin ping -h localhost -prootpassword"
          --health-interval=5s
          --health-timeout=5s
          --health-retries=10

    steps:
      - name: 코드 가져오기
        uses: actions/checkout@v4

      - name: JDK 21 설치
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: gradlew 실행 권한 주기
        run: chmod +x ./gradlew

      - name: 빌드 및 테스트
        run: ./gradlew build
        env:
          SPRING_DATASOURCE_URL: jdbc:mysql://localhost:3306/devops
          SPRING_DATASOURCE_USERNAME: root
          SPRING_DATASOURCE_PASSWORD: rootpassword
```

name은 CI로 지정했지만 아무거나 정해도 됩니다.
on은 언제 이 워크플로우를 실행할지 정하는 부분입니다. 처음 이야기했던 main 브랜치에 push 할 때와 pull request가 생겼을 때 모두 이 워크플로우가 실행됩니다.
특히 pull request는 합치기 전에 미리 검사하기 위해 넣습니다. 현재는 혼자서 하는 토이 프로젝트이지만 실무에서 좋은 습관입니다.
jobs는 워크플로우가 할 일들 목록입니다. compose.yaml의 services와 비슷한 위치입니다.
build 역시 제가 정한 이름입니다. 임의로 정할 수 있습니다.
runs-on 부분은 GitHub Actions가 구동할 임시 컴퓨터를 결정하는 겁니다. 이 프로젝트에선 최신 버전 ubuntu 리눅스를 골랐습니다.