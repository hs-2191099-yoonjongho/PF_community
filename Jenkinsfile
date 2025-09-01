pipeline {
  agent any

  options {
    timestamps()
    skipDefaultCheckout(true)
    disableConcurrentBuilds()                       // 같은 잡 동시 실행 방지
    buildDiscarder(logRotator(numToKeepStr: '20'))  // 최근 20개만 보관
  }

  parameters {
    string(name: 'GIT_CREDENTIALS_ID', defaultValue: '', description: 'Optional: private repo credentials ID (leave empty for public repos)')
  string(name: 'GIT_BRANCH', defaultValue: 'branch(v7)', description: '빌드할 브랜치(멀티브랜치가 아닌 단일 파이프라인일 때 사용). 예: main, develop, branch(v7)')
  booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: 'Skip tests (default=false). CI는 기본적으로 테스트를 실행합니다.')
    string(name: 'AWS_ACCOUNT_ID', defaultValue: '', description: 'AWS 계정 ID')
    string(name: 'AWS_REGION', defaultValue: 'ap-northeast-2', description: 'AWS 리전')
  // 배포 타겟별 자원
  string(name: 'PROD_EC2_INSTANCE_ID', defaultValue: '', description: 'Production EC2 인스턴스 ID (main 배포 대상)')
    string(name: 'AWS_CREDENTIALS_ID', defaultValue: 'aws-jenkins-accesskey', description: 'Jenkins에 설정된 AWS 자격증명 ID')
    string(name: 'DEPLOY_ROLE_ARN', defaultValue: '', description: 'AWS IAM 배포 역할 ARN')
  // 운영 환경 SSM 프리픽스만 사용 (develop는 CI만)
  string(name: 'PROD_SSM_PREFIX', defaultValue: '/community-portfolio/prod', description: 'SSM 파라미터 프리픽스 (main/master → prod)')
  booleanParam(name: 'RUN_TESTS_ON_DEPLOY', defaultValue: false, description: '배포 브랜치(main/master)에서도 테스트를 실행할지 여부(기본: 실행 안 함)')
  booleanParam(name: 'REQUIRE_PROD_APPROVAL', defaultValue: true, description: '운영 배포 전 수동 승인 필요 여부')

  // 테스트 스킵 옵션 (필요 시 사용)
  booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: '테스트 스킵 (기본값: 실행함)')
  }

  environment {
    TZ = 'Asia/Seoul'
    // Use node-global Gradle cache to persist across workspaces
    GRADLE_USER_HOME = "${JENKINS_HOME}/.gradle"
    // AWS ECR 환경 변수
    AWS_REGION = "${params.AWS_REGION}"
    ACCOUNT_ID = "${params.AWS_ACCOUNT_ID}"
    ECR_REPO = "${params.AWS_ACCOUNT_ID}.dkr.ecr.${params.AWS_REGION}.amazonaws.com/community-portfolio"
    IMAGE_TAG = "${env.BUILD_NUMBER}"
    DEPLOY_ROLE_ARN = "${params.DEPLOY_ROLE_ARN}"
  // 동적 매핑(Init 단계에서 세팅):
  // DEPLOY_TARGET in [none|staging|prod]
  // DEPLOY_ENABLED in [true|false]
  // DEPLOY_EC2_INSTANCE_ID, SSM_PREFIX, IMAGE_CHANNEL_TAG
  }

  stages {
    stage('Checkout') {
      steps {
        script {
          def remote = [url: 'https://github.com/hs-2191099-yoonjongho/PF_community.git']
          if (params.GIT_CREDENTIALS_ID?.trim()) {
            remote.credentialsId = params.GIT_CREDENTIALS_ID.trim()
          }
          // 멀티브랜치 파이프라인이면 env.BRANCH_NAME 제공됨. 없으면 파라미터 GIT_BRANCH를 폴백으로 사용
          def fallback = params.GIT_BRANCH?.trim() ? "*/${params.GIT_BRANCH.trim()}" : '*/main'
          def branchToBuild = env.BRANCH_NAME?.trim() ? "*/${env.BRANCH_NAME.trim()}" : fallback
          checkout([
            $class: 'GitSCM',
            branches: [[name: branchToBuild]],                 // 트리거된 브랜치 체크아웃
            userRemoteConfigs: [remote],
            extensions: [[
              $class: 'CloneOption', shallow: true, depth: 1, noTags: true, timeout: 20
            ]]
          ])
        }
      }
    }

    stage('Init (branch → env mapping)') {
      steps {
        script {
          // 브랜치 결정
          def detected = env.BRANCH_NAME?.trim()
          if (!detected) {
            detected = sh(script: 'git rev-parse --abbrev-ref HEAD', returnStdout: true).trim()
          }
          env.GIT_BRANCH_NAME = detected

          // 기본값
          env.DEPLOY_TARGET = 'none'
          env.DEPLOY_ENABLED = 'false'
          env.DEPLOY_EC2_INSTANCE_ID = ''
          env.IMAGE_CHANNEL_TAG = ''

          if (detected == 'main' || detected == 'master') {
            env.DEPLOY_TARGET = 'prod'
            env.DEPLOY_ENABLED = 'true'
            env.DEPLOY_EC2_INSTANCE_ID = params.PROD_EC2_INSTANCE_ID?.trim()
            env.SSM_PREFIX = params.PROD_SSM_PREFIX?.trim()
            env.IMAGE_CHANNEL_TAG = 'latest'
          }

          echo "Branch: ${env.GIT_BRANCH_NAME}, DeployTarget: ${env.DEPLOY_TARGET}, DeployEnabled: ${env.DEPLOY_ENABLED}"
          echo "EC2_INSTANCE_ID: ${env.DEPLOY_EC2_INSTANCE_ID ?: '(empty)'} | SSM_PREFIX: ${env.SSM_PREFIX ?: '(empty)'} | ImageTag: ${env.IMAGE_CHANNEL_TAG ?: '(n/a)'}"
        }
      }
    }

    stage('Prepare') {
      steps {
        sh 'chmod +x gradlew || true'
        sh 'docker --version || echo "WARNING: Docker not available"'
      }
    }

    stage('Build') {
      steps {
        sh './gradlew --version'
        script {
          // 기본: CI는 테스트 수행, 배포 브랜치(main/master)는 기본 스킵. 필요시 RUN_TESTS_ON_DEPLOY=true로 강제 수행.
          def shouldSkip = params.SKIP_TESTS || (env.DEPLOY_ENABLED == 'true' && !params.RUN_TESTS_ON_DEPLOY)
          echo "Build: shouldSkipTests=${shouldSkip} (DEPLOY_ENABLED=${env.DEPLOY_ENABLED}, SKIP_TESTS=${params.SKIP_TESTS}, RUN_TESTS_ON_DEPLOY=${params.RUN_TESTS_ON_DEPLOY})"

          if (shouldSkip) {
            sh './gradlew --no-daemon clean assemble -x test'
          } else {
            // application.properties와 application-test.yml이 테스트 환경을 설정하므로
            // 추가 시스템 속성 없이 Gradle 빌드 실행
            echo 'Running build with tests using H2 in-memory database (configured via application-test.yml)'
            sh './gradlew --no-daemon clean build'
          }
        }
      }
    }

  stage('Test Reports') {
  when { expression { return !params.SKIP_TESTS && !(env.DEPLOY_ENABLED == 'true' && !params.RUN_TESTS_ON_DEPLOY) } }
      steps {
        junit allowEmptyResults: true, testResults: 'build/test-results/test/**/*.xml'
  archiveArtifacts allowEmptyArchive: true, artifacts: 'build/reports/tests/test/**'
      }
    }

    stage('Archive') {
      steps {
        archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true, onlyIfSuccessful: true
      }
    }
    
    stage('Who am I') {
      when { expression { return params.AWS_CREDENTIALS_ID?.trim() } }
      steps {
        withCredentials([[$class:'AmazonWebServicesCredentialsBinding', credentialsId: params.AWS_CREDENTIALS_ID]]) {
          sh 'aws sts get-caller-identity'
        }
      }
    }
    
    stage('AssumeRole') {
      when {
        expression { return env.DEPLOY_ENABLED == 'true' && params.DEPLOY_ROLE_ARN?.trim() && params.AWS_ACCOUNT_ID?.trim() && (env.DEPLOY_EC2_INSTANCE_ID?.trim()) }
      }
      steps {
        withCredentials([[$class:'AmazonWebServicesCredentialsBinding', credentialsId: params.AWS_CREDENTIALS_ID]]) {
          sh '''
            set -e
            CREDS=$(aws sts assume-role \\
              --role-arn "${DEPLOY_ROLE_ARN}" \\
              --role-session-name jenkins-deploy \\
              --duration-seconds 3600 \\
              --query 'Credentials.[AccessKeyId,SecretAccessKey,SessionToken]' \\
              --output text)
            AK=$(echo "$CREDS" | awk '{print $1}')
            SK=$(echo "$CREDS" | awk '{print $2}')
            ST=$(echo "$CREDS" | awk '{print $3}')
            {
              echo "export AWS_ACCESS_KEY_ID=${AK}"
              echo "export AWS_SECRET_ACCESS_KEY=${SK}"
              echo "export AWS_SESSION_TOKEN=${ST}"
              echo "export AWS_DEFAULT_REGION=${AWS_REGION}"
            } > aws_env_export
            echo "Wrote temporary AWS creds to aws_env_export"
          '''
        }
      }
    }
    
    stage('Build & Push Docker Image') {
      when { expression { return env.DEPLOY_ENABLED == 'true' && params.DEPLOY_ROLE_ARN?.trim() && params.AWS_ACCOUNT_ID?.trim() && (env.DEPLOY_EC2_INSTANCE_ID?.trim()) } }
      steps {
        sh '''
          set -e
          . ./aws_env_export

          echo "Validating temporary credentials..."
          aws sts get-caller-identity

          REPO=${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/community-portfolio
          SHA=$(git rev-parse --short HEAD)
          CHANNEL_TAG=${IMAGE_CHANNEL_TAG}
          if [ -z "$CHANNEL_TAG" ]; then CHANNEL_TAG=latest; fi

          echo "Building and pushing image to ECR via Jib (no Docker daemon)..."
          ./gradlew --no-daemon jib \
            -Djib.to.image=$REPO:$CHANNEL_TAG \
            -Djib.to.auth.username=AWS \
            -Djib.to.auth.password="$(aws ecr get-login-password --region ${AWS_REGION})"

          echo "Tagging commit SHA as well..."
          ./gradlew --no-daemon jib \
            -Djib.to.image=$REPO:$SHA \
            -Djib.to.auth.username=AWS \
            -Djib.to.auth.password="$(aws ecr get-login-password --region ${AWS_REGION})"
        '''
      }
    }

    stage('Approve Production Deploy') {
      when { expression { return env.DEPLOY_TARGET == 'prod' && params.REQUIRE_PROD_APPROVAL } }
      steps {
        timeout(time: 10, unit: 'MINUTES') {
          input message: '운영 배포를 진행할까요?', ok: 'Deploy'
        }
      }
    }

    stage('Deploy to EC2 (via docker-compose)') {
      when { expression { return env.DEPLOY_ENABLED == 'true' && params.DEPLOY_ROLE_ARN?.trim() && params.AWS_ACCOUNT_ID?.trim() && (env.DEPLOY_EC2_INSTANCE_ID?.trim()) } }
      steps {
        sh '''
          set -e
          . ./aws_env_export

          echo "Verifying AWS credentials for EC2 deployment..."
          aws sts get-caller-identity >/dev/null

          echo "Checking required SSM parameters under prefix: ${SSM_PREFIX}"
          REQUIRED_KEYS="
${SSM_PREFIX}/db/url
${SSM_PREFIX}/db/user
${SSM_PREFIX}/db/pass
${SSM_PREFIX}/jwt/secret
${SSM_PREFIX}/jwt/access-exp-ms
${SSM_PREFIX}/refresh/exp-ms
${SSM_PREFIX}/refresh/cookie/name
${SSM_PREFIX}/refresh/cookie/path
${SSM_PREFIX}/refresh/cookie/secure
${SSM_PREFIX}/refresh/cookie/same-site
${SSM_PREFIX}/allowed-origins
${SSM_PREFIX}/public-base-url
"
          # domain은 필수가 아님 (EC2 IP로 대체 가능)
          for key in $REQUIRED_KEYS; do
            if ! aws ssm get-parameter --name "$key" --with-decryption --query Parameter.Name --output text >/dev/null 2>&1; then
              echo "Missing required SSM parameter: $key" >&2
              exit 1
            fi
          done

          # EC2 인스턴스 상태 확인
          echo "Checking EC2 instance status..."
          EC2_STATUS=$(aws ec2 describe-instances --instance-ids ${DEPLOY_EC2_INSTANCE_ID} --query 'Reservations[0].Instances[0].State.Name' --output text)
          echo "EC2 instance status: $EC2_STATUS"
          
          if [ "$EC2_STATUS" != "running" ]; then
            echo "ERROR: EC2 instance is not running (current state: $EC2_STATUS)"
            exit 1
          fi
          
      # 디렉토리 생성 및 파일 쓰기 명령을 위한 JSON 파일 생성 (.env 및 compose 파일 배포)
          echo "Creating JSON file for SSM command..."
      cat > ssm-params.json <<EOF
{
  "commands": [
    "mkdir -p /opt/community-portfolio",
    "mkdir -p /opt/community-portfolio/uploads",
      "chown -R 10001:10001 /opt/community-portfolio/uploads || true",
      "chmod 770 /opt/community-portfolio/uploads || true",
    "ls -la /opt/community-portfolio"
  ]
}
EOF

          # SSM 명령 실행
          echo "Sending SSM command to create docker-compose.yml..."
          WRITE_CMD_ID=$(aws ssm send-command \
            --document-name "AWS-RunShellScript" \
            --instance-ids "${DEPLOY_EC2_INSTANCE_ID}" \
            --comment "Write docker-compose.yml" \
            --parameters file://ssm-params.json \
            --output text \
            --query "Command.CommandId")

          echo "Waiting for file creation to complete (Command ID: $WRITE_CMD_ID)..."
          aws ssm wait command-executed \
            --command-id "$WRITE_CMD_ID" \
            --instance-id "${DEPLOY_EC2_INSTANCE_ID}" || echo "Wait command timed out, but continuing..."

          # 명령 결과 확인
          echo "Checking file creation results..."
          FILE_RESULT=$(aws ssm get-command-invocation \
            --command-id "$WRITE_CMD_ID" \
            --instance-id "${DEPLOY_EC2_INSTANCE_ID}" \
            --query "Status" \
            --output text)
            
          echo "File creation status: $FILE_RESULT"
          
          if [ "$FILE_RESULT" != "Success" ]; then
            echo "Error creating docker-compose.yml file:"
            aws ssm get-command-invocation \
              --command-id "$WRITE_CMD_ID" \
              --instance-id "${DEPLOY_EC2_INSTANCE_ID}" \
              --query "StandardErrorContent" \
              --output text
            exit 1
          fi

          # 애플리케이션 배포 명령 JSON 생성
          echo "Building remote deploy script (deploy.sh with docker compose)..."
          # 1) 원격에서 실행할 배포 스크립트를 로컬에서 생성 (변수 확장 방지를 위해 리터럴 heredoc 사용)
          cat > deploy-remote.sh <<'EOS'
#!/usr/bin/env bash
set -euo pipefail

REGION="__AWS_REGION__"
ACCOUNT_ID="__ACCOUNT_ID__"
SSM_PREFIX="__SSM_PREFIX__"
IMAGE_TAG="__IMAGE_TAG__"

cd /opt/community-portfolio
mkdir -p uploads
chmod 755 . || true
chown -R 10001:10001 uploads || true
chmod 770 uploads || true

# Docker 실행 확인 및 시작 (systemd가 없으면 무시)
if command -v systemctl >/dev/null 2>&1; then
  systemctl is-active docker >/dev/null 2>&1 || systemctl start docker || true
fi

# ECR 로그인 및 최신 이미지 pull
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com"
docker pull "$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/community-portfolio:${IMAGE_TAG}" || true
docker rm -f community-app || true || true

# --- SSM → 환경변수 로드 ---
DB_URL=$(aws ssm get-parameter --name "$SSM_PREFIX/db/url" --with-decryption --query 'Parameter.Value' --output text)
DB_USER=$(aws ssm get-parameter --name "$SSM_PREFIX/db/user" --with-decryption --query 'Parameter.Value' --output text)
DB_PASS=$(aws ssm get-parameter --name "$SSM_PREFIX/db/pass" --with-decryption --query 'Parameter.Value' --output text)

# 쿠키 도메인은 1차 도메인 사용 권장. 미설정 시 비워둠(브라우저 규칙 상 IP는 권장되지 않음)
JWT_SECRET=$(aws ssm get-parameter --name "$SSM_PREFIX/jwt/secret" --with-decryption --query 'Parameter.Value' --output text)
JWT_ACCESS_EXP_MS=$(aws ssm get-parameter --name "$SSM_PREFIX/jwt/access-exp-ms" --with-decryption --query 'Parameter.Value' --output text)
JWT_ISSUER=$(aws ssm get-parameter --name "$SSM_PREFIX/jwt/issuer" --with-decryption --query 'Parameter.Value' --output text 2>/dev/null || echo "community-app")
REFRESH_EXP_MS=$(aws ssm get-parameter --name "$SSM_PREFIX/refresh/exp-ms" --with-decryption --query 'Parameter.Value' --output text)
REFRESH_COOKIE_NAME=$(aws ssm get-parameter --name "$SSM_PREFIX/refresh/cookie/name" --with-decryption --query 'Parameter.Value' --output text)
REFRESH_COOKIE_PATH=$(aws ssm get-parameter --name "$SSM_PREFIX/refresh/cookie/path" --with-decryption --query 'Parameter.Value' --output text)
REFRESH_COOKIE_SECURE=$(aws ssm get-parameter --name "$SSM_PREFIX/refresh/cookie/secure" --with-decryption --query 'Parameter.Value' --output text)
REFRESH_COOKIE_SAME_SITE=$(aws ssm get-parameter --name "$SSM_PREFIX/refresh/cookie/same-site" --with-decryption --query 'Parameter.Value' --output text)
REFRESH_COOKIE_DOMAIN=$(aws ssm get-parameter --name "$SSM_PREFIX/refresh/cookie/domain" --with-decryption --query 'Parameter.Value' --output text 2>/dev/null || echo "")
ALLOWED_ORIGINS=$(aws ssm get-parameter --name "$SSM_PREFIX/allowed-origins" --with-decryption --query 'Parameter.Value' --output text)
S3_BUCKET=$(aws ssm get-parameter --name "$SSM_PREFIX/s3/bucket" --with-decryption --query 'Parameter.Value' --output text 2>/dev/null || echo "")
PUBLIC_BASE_URL=$(aws ssm get-parameter --name "$SSM_PREFIX/public-base-url" --with-decryption --query 'Parameter.Value' --output text 2>/dev/null || echo "")


ADMIN_EMAIL=$(aws ssm get-parameter --name "$SSM_PREFIX/admin/email" --with-decryption --query 'Parameter.Value' --output text 2>/dev/null || echo "")
ADMIN_USERNAME=$(aws ssm get-parameter --name "$SSM_PREFIX/admin/username" --with-decryption --query 'Parameter.Value' --output text 2>/dev/null || echo "")
ADMIN_PASSWORD_HASH=$(aws ssm get-parameter --name "$SSM_PREFIX/admin/password-hash" --with-decryption --query 'Parameter.Value' --output text 2>/dev/null || echo "")

# 데이터베이스 존재 보장 (최초 배포 대비)
# JDBC URL 파싱은 bash 문자열 연산으로 안전하게 처리
# 예: jdbc:mysql://host:3306/board?param=...
db_url_no_prefix="${DB_URL#jdbc:mysql://}"
hostport_and_path="${db_url_no_prefix%%/*}"
DB_HOST="${hostport_and_path%%:*}"
DB_PORT="${hostport_and_path##*:}"
# 포트가 없으면 기본 3306
if [ "$DB_PORT" = "$hostport_and_path" ] || [ -z "$DB_PORT" ]; then DB_PORT="3306"; fi
path_after_slash="${db_url_no_prefix#*/}"
# 쿼리스트링 제거: '?' 기준 첫 필드
DB_NAME=$(printf '%s\n' "$path_after_slash" | cut -d'?' -f1)
if [ -n "$DB_HOST" ] && [ -n "$DB_NAME" ]; then
  echo "Ensuring database '$DB_NAME' exists on $DB_HOST:$DB_PORT..."
  # mysql 클라이언트를 컨테이너로 일회성 실행 (로컬 설치 불필요)
  docker run --rm mysql:8 \
    mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" \
      -e "CREATE DATABASE IF NOT EXISTS $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;" \
  || echo "WARN: Could not ensure database exists (non-fatal)."
else
  echo "WARN: Could not parse DB URL: $DB_URL"
fi

# 환경변수 디버그 출력
echo "DEBUG: Environment variables loaded:"
echo "  DB_URL=$DB_URL"
echo "  DB_USER=$DB_USER" 
echo "  JWT_SECRET=[MASKED]"
echo "  REFRESH_EXP_MS=$REFRESH_EXP_MS"
echo "  REFRESH_COOKIE_DOMAIN=$REFRESH_COOKIE_DOMAIN"
echo "  PUBLIC_BASE_URL=$PUBLIC_BASE_URL"
echo "  ALLOWED_ORIGINS=$ALLOWED_ORIGINS"
echo "  ADMIN_EMAIL=$([ -n "$ADMIN_EMAIL" ] && echo set || echo empty)"

# 설정 검증: 위험한 ALLOWED_ORIGINS 값 방지
if [ "${ALLOWED_ORIGINS}" = "*" ]; then
  echo "ERROR: ALLOWED_ORIGINS must not be '*' in production." >&2
  exit 1
fi

# Admin seed validation: if any ADMIN_* is set, require all three
if [ -n "$ADMIN_EMAIL" ] || [ -n "$ADMIN_USERNAME" ] || [ -n "$ADMIN_PASSWORD_HASH" ]; then
  if [ -z "$ADMIN_EMAIL" ] || [ -z "$ADMIN_USERNAME" ] || [ -z "$ADMIN_PASSWORD_HASH" ]; then
    echo "ERROR: ADMIN_* parameters are partially provided. Require all of ADMIN_EMAIL, ADMIN_USERNAME, ADMIN_PASSWORD_HASH." >&2
    exit 1
  fi
fi

# docker compose용 동적 .env 생성 (compose의 ${VAR} 치환을 사용)
cat > .env <<ENV
SPRING_PROFILES_ACTIVE=prod
DB_URL=$DB_URL
DB_USERNAME=$DB_USER
DB_PASSWORD=$DB_PASS
JWT_SECRET=$JWT_SECRET
JWT_ACCESS_EXP_MS=$JWT_ACCESS_EXP_MS
JWT_ISSUER=$JWT_ISSUER
REFRESH_EXP_MS=$REFRESH_EXP_MS
REFRESH_COOKIE_NAME=$REFRESH_COOKIE_NAME
REFRESH_COOKIE_PATH=$REFRESH_COOKIE_PATH
REFRESH_COOKIE_SECURE=$REFRESH_COOKIE_SECURE
REFRESH_COOKIE_SAME_SITE=$REFRESH_COOKIE_SAME_SITE
REFRESH_COOKIE_DOMAIN=$REFRESH_COOKIE_DOMAIN
ALLOWED_ORIGINS=$ALLOWED_ORIGINS
PUBLIC_BASE_URL=${PUBLIC_BASE_URL}
S3_BUCKET=$S3_BUCKET
AWS_REGION=$REGION
ACCOUNT_ID=$ACCOUNT_ID
SERVER_PORT=8080
ADMIN_EMAIL=$ADMIN_EMAIL
ADMIN_USERNAME=$ADMIN_USERNAME
ADMIN_PASSWORD_HASH=$ADMIN_PASSWORD_HASH
ENV

# docker-compose 파일 생성 (.env의 ${VAR} 사용)
cat > docker-compose.yml <<YML
version: '3.8'
services:
  app:
    image: ${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/community-portfolio:${IMAGE_TAG}
    container_name: community-app
    restart: unless-stopped
    ports:
      - "${SERVER_PORT}:${SERVER_PORT}"
    environment:
      SPRING_PROFILES_ACTIVE: "${SPRING_PROFILES_ACTIVE}"
      DB_URL: "${DB_URL}"
      DB_USERNAME: "${DB_USERNAME}"
      DB_PASSWORD: "${DB_PASSWORD}"
      JWT_SECRET: "${JWT_SECRET}"
      JWT_ACCESS_EXP_MS: "${JWT_ACCESS_EXP_MS}"
      JWT_ISSUER: "${JWT_ISSUER}"
      REFRESH_EXP_MS: "${REFRESH_EXP_MS}"
      REFRESH_COOKIE_NAME: "${REFRESH_COOKIE_NAME}"
      REFRESH_COOKIE_PATH: "${REFRESH_COOKIE_PATH}"
      REFRESH_COOKIE_SECURE: "${REFRESH_COOKIE_SECURE}"
      REFRESH_COOKIE_SAME_SITE: "${REFRESH_COOKIE_SAME_SITE}"
      REFRESH_COOKIE_DOMAIN: "${REFRESH_COOKIE_DOMAIN}"
      ALLOWED_ORIGINS: "${ALLOWED_ORIGINS}"
      PUBLIC_BASE_URL: "${PUBLIC_BASE_URL}"
      S3_BUCKET: "${S3_BUCKET}"
      SERVER_PORT: "${SERVER_PORT}"
      ADMIN_EMAIL: "${ADMIN_EMAIL}"
      ADMIN_USERNAME: "${ADMIN_USERNAME}"
      ADMIN_PASSWORD_HASH: "${ADMIN_PASSWORD_HASH}"
    volumes:
      - ./uploads:/app/uploads
    # 컨테이너 내 curl 비존재 가능성 때문에 healthcheck 제거 (외부에서 SSM로 헬스 확인)
YML

docker compose pull || true
docker compose up -d

echo "Waiting for health (up to ~90s)..."
for i in $(seq 1 30); do
  if curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
    echo "Health check passed."
    exit 0
  fi
  sleep 3
done
echo "Health check timed out. Showing logs..."
docker compose logs --no-color | tail -n 200 || true
exit 1
EOS

          # 2) 자리표시자 치환 (안전한 구분자 사용)
          sed -i "s|__ACCOUNT_ID__|${ACCOUNT_ID}|g; s|__AWS_REGION__|${AWS_REGION}|g; s|__SSM_PREFIX__|${SSM_PREFIX}|g; s|__IMAGE_TAG__|${IMAGE_CHANNEL_TAG}|g" deploy-remote.sh

          # 3) base64 인코딩 후 SSM으로 스크립트 전송 및 실행
          if base64 --help 2>&1 | grep -q -- "-w"; then
            DEPLOY_B64=$(base64 -w0 deploy-remote.sh)
          else
            DEPLOY_B64=$(base64 deploy-remote.sh | tr -d '\n')
          fi

  cat > deploy-params.json <<EOF
{
  "commands": [
    "mkdir -p /opt/community-portfolio",
    "mkdir -p /opt/community-portfolio/uploads", 
      "chown -R 10001:10001 /opt/community-portfolio/uploads || true",
      "chmod 770 /opt/community-portfolio/uploads || true",
    "echo '${DEPLOY_B64}' | base64 -d > /opt/community-portfolio/deploy.sh",
    "chmod +x /opt/community-portfolio/deploy.sh",
    "/opt/community-portfolio/deploy.sh"
  ]
}
EOF

          echo "Deploying application via SSM..."
          DEPLOY_CMD_ID=$(aws ssm send-command \
            --document-name "AWS-RunShellScript" \
            --instance-ids "${DEPLOY_EC2_INSTANCE_ID}" \
            --comment "Deploy application" \
            --parameters file://deploy-params.json \
            --output text \
            --query "Command.CommandId")

          echo "Polling deployment status (Command ID: $DEPLOY_CMD_ID)..."
          POLL_MAX=120   # 최대 약 10분 (120 * 5초)
          POLL_INTERVAL=5
          DEPLOY_RESULT="Pending"
          for i in $(seq 1 $POLL_MAX); do
            # 간헐적 오류(InvocationDoesNotExist 등) 무시하고 재시도
            DEPLOY_RESULT=$(aws ssm get-command-invocation \
              --command-id "$DEPLOY_CMD_ID" \
              --instance-id "${DEPLOY_EC2_INSTANCE_ID}" \
              --query "Status" \
              --output text 2>/dev/null || echo "Unknown")
            echo "Deployment status: $DEPLOY_RESULT ($i/$POLL_MAX)"

            case "$DEPLOY_RESULT" in
              Success)
                break ;;
              Failed|Cancelled|TimedOut)
                break ;;
              InProgress|Pending|Delayed|Cancelling|Unknown)
                sleep $POLL_INTERVAL ;;
              *)
                sleep $POLL_INTERVAL ;;
            esac
          done

          if [ "$DEPLOY_RESULT" != "Success" ]; then
            echo "ERROR: Deployment did not succeed (final status: $DEPLOY_RESULT). Dumping logs..."
            aws ssm get-command-invocation \
              --command-id "$DEPLOY_CMD_ID" \
              --instance-id "${DEPLOY_EC2_INSTANCE_ID}" \
              --query "{Error:StandardErrorContent, Output:StandardOutputContent}" \
              --output json || true
            exit 1
          fi

          echo "Deployment succeeded! Application should be running at http://<EC2-PUBLIC-IP>:8080"
          aws ssm get-command-invocation \
            --command-id "$DEPLOY_CMD_ID" \
            --instance-id "${DEPLOY_EC2_INSTANCE_ID}" \
            --query "StandardOutputContent" \
            --output text || true
        '''
      }
    }
  }

  post {
    always {
      // Clean workspace; fallback to deleteDir() if Workspace Cleanup plugin is not installed
      script {
        try {
          cleanWs()
        } catch (Exception e) {
          echo 'cleanWs() not available, falling back to deleteDir()'
          deleteDir()
        }
      }
    }
    failure {
      echo 'Build or deployment failed. Check logs and test reports.'
    }
    success {
      echo 'Build and deployment succeeded. Application is now running on EC2.'
    }
  }
}
