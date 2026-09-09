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

## Day 4
### CD 설정과 민감 정보 분리, 데이터 영속성
CI에 이어서 CD(이미지를 저장소에 올리기)를 설정하고, 하드코딩된 비밀번호를 분리하며, 볼륨으로 데이터를 유지시킵니다.

CD는 크게 두 가지로 나뉩니다. 검증된 이미지를 배포 가능한 상태로 저장소에 준비해두는 Continuous Delivery(지속적 전달)와, 실제 서버에 올리는 것까지 자동화하는 Continuous Deployment(지속적 배포)입니다. 이 프로젝트에서는 이미지를 GHCR(GitHub Container Registry)에 올리는 것까지 하므로 Continuous Delivery에 해당합니다.

GHCR은 Docker 이미지를 보관하는 창고(레지스트리)입니다. 이미지를 굽는 곳(CI 서버)과 실행하는 곳(배포 서버)이 서로 다르기 때문에, 구운 이미지를 저장소에 올려두면 어디서든 가져다 쓸 수 있습니다. AWS를 사용하는 실무에서는 이 자리에 보통 ECR을 쓰지만, 로그인 → 굽기 → push라는 흐름은 동일합니다.

### CD 설정 (이미지를 GHCR에 push)
#### 권한 추가하기
GHCR에 이미지를 올리려면 쓰기 권한이 필요합니다. ci.yml의 on 블록과 jobs 블록 사이에 아래 내용을 추가합니다.

```yaml
permissions:
  contents: read
  packages: write
```
contents: read는 코드를 읽는 권한(코드 가져오기용), packages: write는 패키지(이미지)를 저장소에 올리는 권한입니다. packages: write가 없으면 push 단계에서 권한 없음으로 막힙니다.

#### CD 단계 추가하기
ci.yml의 steps 맨 아래에 아래 두 단계를 추가합니다.

```yaml
      - name: GHCR 로그인
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: 이미지 굽고 GHCR에 push
        uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          tags: ghcr.io/${{ github.repository }}:latest
```
GHCR 로그인 단계는 저장소에 이미지를 올리기 위한 인증입니다.
username의 ${{ github.actor }}는 워크플로를 실행시킨 사람(push한 사용자)을 자동으로 넣어줍니다.
password의 ${{ secrets.GITHUB_TOKEN }}은 GitHub Actions가 워크플로가 실행될 때마다 자동으로 발급하는 임시 토큰입니다. 내가 직접 발급하거나 저장해둔 토큰이 아니며, 워크플로가 끝나면 폐기되는 일회용입니다. 덕분에 비밀번호를 직접 파일에 적지 않아도 됩니다.

이미지 굽고 push하는 단계가 CD의 핵심입니다.
context: .는 현재 디렉토리의 Dockerfile과 코드로 빌드하라는 의미로, docker build .의 점과 같습니다.
push: true는 이미지를 굽기만 하는 것이 아니라 저장소에 올리라는 의미입니다.
tags는 이미지에 붙일 이름표입니다. ${{ github.repository }}가 저장소 경로(사용자명/저장소명)를 자동으로 넣어주어, 최종적으로 ghcr.io/사용자명/저장소명:latest 형태가 됩니다.

push하면 CI(빌드/테스트)에 이어 CD(이미지 push)까지 자동으로 실행되고, 저장소 메인 페이지의 Packages 항목에서 올라간 이미지를 확인할 수 있습니다.

### 민감 정보 분리
지금까지 비밀번호가 여러 파일에 평문(하드코딩)으로 적혀 있었습니다. 실제로는 일회용 테스트 DB의 비밀번호라 위험이 크진 않지만, 실무 습관을 연습하기 위해 코드 밖으로 분리합니다.
분리 방식은 환경에 따라 다릅니다. 로컬(compose.yaml)은 .env 파일을, GitHub Actions(ci.yml)는 GitHub Secrets를 사용합니다.

#### 로컬 - .env 파일로 분리
Docker Compose는 프로젝트 루트에 .env 파일이 있으면 자동으로 읽어 ${변수명} 자리에 값을 넣어줍니다.

```bash
code .env
```

```
MYSQL_ROOT_PASSWORD=rootpassword
MYSQL_DATABASE=devops
SPRING_DATASOURCE_PASSWORD=rootpassword
```

compose.yaml에서 평문 값을 참조로 바꿉니다.

```yaml
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${MYSQL_DATABASE}
```
healthcheck의 -prootpassword도 -p${MYSQL_ROOT_PASSWORD}로 바꿔 일관성을 맞춥니다. (-p와 값은 반드시 붙여 씁니다.)

가장 중요한 단계는 .env를 gitignore에 넣는 것입니다. 이걸 하지 않으면 비밀번호가 그대로 저장소에 올라가 분리한 의미가 없어집니다.
```bash
echo ".env" >> .gitignore
```

대신 .env.example이라는 양식 파일을 만들어 올립니다. 값은 비우거나 기본값만 채워, 나중에 이 프로젝트를 받는 사람이 어떤 변수가 필요한지 알 수 있게 합니다. 이 파일은 gitignore하지 않습니다.
```bash
code .env.example
```

```
MYSQL_ROOT_PASSWORD=
MYSQL_DATABASE=devops
SPRING_DATASOURCE_PASSWORD=
```

#### GitHub Actions - Secrets로 분리
GitHub Secrets는 GitHub Actions용 금고입니다. .env가 로컬용 금고라면, Secrets는 GitHub 서버에 암호화되어 저장됩니다.

저장소의 Settings → Secrets and variables → Actions → New repository secret에서 secret을 등록합니다.
- Name: DB_PASSWORD
- Secret: rootpassword

ci.yml에서 평문 비밀번호를 참조로 바꿉니다. MySQL 서비스, healthcheck, 빌드/테스트 단계 세 곳 모두 같은 secret을 참조하게 합니다.

```yaml
        env:
          MYSQL_ROOT_PASSWORD: ${{ secrets.DB_PASSWORD }}
          MYSQL_DATABASE: devops
```
```yaml
        options: >-
          --health-cmd="mysqladmin ping -h localhost -p${{ secrets.DB_PASSWORD }}"
```
```yaml
        env:
          SPRING_DATASOURCE_URL: jdbc:mysql://localhost:3306/devops
          SPRING_DATASOURCE_USERNAME: root
          SPRING_DATASOURCE_PASSWORD: ${{ secrets.DB_PASSWORD }}
```
${{ secrets.DB_PASSWORD }}는 GitHub 금고에서 해당 secret을 가져와 넣으라는 의미입니다. CD에서 쓴 ${{ secrets.GITHUB_TOKEN }}과 문법은 같고, GITHUB_TOKEN은 자동 발급, DB_PASSWORD는 내가 직접 등록했다는 점만 다릅니다.

참고로 .env는 ${변수명}(중괄호 한 겹), GitHub Actions는 ${{ ... }}(중괄호 두 겹)를 씁니다. 서로 다른 도구가 각자의 문법을 쓰는 것이며, 두 겹 중괄호는 GitHub Actions의 표현식(expression) 문법으로 secrets, github 정보 등을 실행 시점에 계산해 넣습니다.

### 데이터 영속성 (볼륨)
docker compose down은 상자를 멈추는 것이 아니라 삭제합니다. MySQL 데이터가 상자 안에 저장되어 있었기 때문에, down을 하면 데이터가 함께 사라져 방문 횟수가 리셋됩니다. (restart는 상자를 살려둔 채 재시작이라 데이터가 유지되지만, down은 상자 자체를 삭제하기 때문입니다.)
이를 해결하기 위해 볼륨을 사용합니다. 볼륨은 상자가 삭제돼도 살아남는 별도의 저장 공간입니다.

compose.yaml 맨 아래에 볼륨을 선언합니다.
```yaml
volumes:
  db_data:
```
volumes는 services와 같은 최상위 항목이고, db_data는 사용자 정의 볼륨 이름입니다.

db 서비스가 이 볼륨을 사용하도록 연결합니다.
```yaml
    volumes:
      - db_data:/var/lib/mysql
```
볼륨이름:상자안경로 형태로, 포트 설정의 바깥:안쪽과 비슷한 구조입니다.
/var/lib/mysql은 MySQL이 상자 안에서 데이터를 저장하는 경로입니다. 이 경로를 상자 바깥의 db_data 볼륨에 연결하면, MySQL이 저장하는 데이터가 볼륨에 쌓여 상자를 삭제해도 유지됩니다.

이제 docker compose down 후 다시 up 해도 방문 횟수가 유지됩니다. Day 2에서 확인한 데이터 유지가 restart뿐 아니라 down에도 견디도록 완성된 것입니다.