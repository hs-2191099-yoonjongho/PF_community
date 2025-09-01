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
    string(name: 'GIT_BRANCH', defaultValue: 'main', description: '빌드할 브랜치(단일 파이프라인일 때). 예: main, develop')
    booleanParam(name: 'DEPLOY', defaultValue: false, description: ' 체크하면 이번 빌드에서 바로 배포까지 진행 (승인 없음)')

    // AWS/ECR
    string(name: 'AWS_ACCOUNT_ID', defaultValue: '', description: 'AWS 계정 ID (12자리)')
    string(name: 'AWS_REGION', defaultValue: 'ap-northeast-2', description: 'AWS 리전')
    string(name: 'AWS_CREDENTIALS_ID', defaultValue: 'aws-jenkins-accesskey', description: 'Jenkins에 설정된 AWS 자격증명 ID')
    string(name: 'DEPLOY_ROLE_ARN', defaultValue: '', description: 'AWS IAM 배포 역할 ARN (AssumeRole 대상)')

    // 운영 배포 타겟/파라미터 스토어
    string(name: 'PROD_EC2_INSTANCE_ID', defaultValue: '', description: 'Production EC2 인스턴스 ID')
    string(name: 'PROD_SSM_PREFIX', defaultValue: '/community-portfolio/dev', description: 'SSM 파라미터 프리픽스')

    // 빌드/운영 승인 옵션
    booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: ' 체크하면 테스트를 실행하지 않음')
  }

  environment {
    TZ = 'Asia/Seoul'
    GRADLE_USER_HOME = "${JENKINS_HOME}/.gradle"

    // AWS/ECR 공통
    AWS_REGION = "${params.AWS_REGION}"
    ACCOUNT_ID = "${params.AWS_ACCOUNT_ID}"
    ECR_REPO  = "${params.AWS_ACCOUNT_ID}.dkr.ecr.${params.AWS_REGION}.amazonaws.com/community-portfolio"
    IMAGE_TAG = "${env.BUILD_NUMBER}"
    DEPLOY_ROLE_ARN = "${params.DEPLOY_ROLE_ARN}"

    // Init에서 채울 동적 매핑:
    // DEPLOY_ENABLED in [true|false]
    // DEPLOY_TARGET in [none|prod]
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
          def resolvedBranch = (env.BRANCH_NAME?.trim()) ?: (params.GIT_BRANCH?.trim()) ?: 'main'
          echo "Checkout target branch: ${resolvedBranch}"

          checkout([
            $class: 'GitSCM',
            userRemoteConfigs: [remote],
            branches: [[name: "*/${resolvedBranch}"]],
            extensions: [
              [$class: 'CloneOption', shallow: true, depth: 1, noTags: true, timeout: 20],
              //  로컬 브랜치로 체크아웃하여 detached HEAD 방지
              [$class: 'LocalBranch', localBranch: "${resolvedBranch}"]
            ]
          ])
        }
      }
    }

    // 브랜치와 무관하게 체크박스로 배포 여부 결정
    stage('Init (deploy toggle)') {
      steps {
        script {
          def cur = sh(script: 'git rev-parse --abbrev-ref HEAD || true', returnStdout: true).trim()
          echo "Current branch (info only): ${cur}"

          env.DEPLOY_ENABLED = params.DEPLOY ? 'true' : 'false'
          env.DEPLOY_TARGET  = params.DEPLOY ? 'dev' : 'none'

          env.DEPLOY_EC2_INSTANCE_ID = params.DEPLOY ? (params.PROD_EC2_INSTANCE_ID?.trim()) : ''
          env.SSM_PREFIX             = params.DEPLOY ? (params.PROD_SSM_PREFIX?.trim())       : ''
          env.IMAGE_CHANNEL_TAG      = params.DEPLOY ? 'latest'                                : ''

          echo "DeployEnabled: ${env.DEPLOY_ENABLED}, Target: ${env.DEPLOY_TARGET}"
          echo "EC2_INSTANCE_ID: ${env.DEPLOY_EC2_INSTANCE_ID ?: '(empty)'} | SSM_PREFIX: ${env.SSM_PREFIX ?: '(empty)'} | ChannelTag: ${env.IMAGE_CHANNEL_TAG ?: '(n/a)'}"
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
          // 테스트 스킵 체크박스로 테스트 실행 여부 결정
          def shouldSkip = params.SKIP_TESTS
          echo "Build: shouldSkipTests=${shouldSkip} (SKIP_TESTS=${params.SKIP_TESTS})"

          if (shouldSkip) {
            sh './gradlew --no-daemon clean assemble -x test'
          } else {
            echo 'Running build with tests using H2 in-memory database (configured via application-test.yml)'
            sh './gradlew --no-daemon clean build'
          }
        }
      }
    }

    stage('Test Reports') {
      when { expression { return !params.SKIP_TESTS } }
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
            CREDS=$(aws sts assume-role \
              --role-arn "${DEPLOY_ROLE_ARN}" \
              --role-session-name jenkins-deploy \
              --duration-seconds 3600 \
              --query 'Credentials.[AccessKeyId,SecretAccessKey,SessionToken]' \
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
      when {
        expression { return env.DEPLOY_ENABLED == 'true' && params.DEPLOY_ROLE_ARN?.trim() && params.AWS_ACCOUNT_ID?.trim() && (env.DEPLOY_EC2_INSTANCE_ID?.trim()) }
      }
      steps {
        sh '''
          set -e
          . ./aws_env_export

          echo "Validating temporary credentials..."
          aws sts get-caller-identity >/dev/null

          REPO=${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/community-portfolio
          SHA=$(git rev-parse --short HEAD)
          CHANNEL_TAG=${IMAGE_CHANNEL_TAG}
          if [ -z "$CHANNEL_TAG" ]; then CHANNEL_TAG=latest; fi

          echo "Building and pushing image to ECR via Jib (no Docker daemon needed)..."
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

    // 승인 단계 제거 - 배포 체크박스를 선택하면 바로 배포됨

    stage('Deploy to EC2 (via docker-compose)') {
      when {
        expression { return env.DEPLOY_ENABLED == 'true' && params.DEPLOY_ROLE_ARN?.trim() && params.AWS_ACCOUNT_ID?.trim() && (env.DEPLOY_EC2_INSTANCE_ID?.trim()) }
      }
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
          for key in $REQUIRED_KEYS; do
            if ! aws ssm get-parameter --name "$key" --with-decryption --query Parameter.Name --output text >/dev/null 2>&1; then
              echo "Missing required SSM parameter: $key" >&2
              exit 1
            fi
          done

          echo "Checking EC2 instance status..."
          EC2_STATUS=$(aws ec2 describe-instances --instance-ids ${DEPLOY_EC2_INSTANCE_ID} --query 'Reservations[0].Instances[0].State.Name' --output text)
          echo "EC2 instance status: $EC2_STATUS"
          if [ "$EC2_STATUS" != "running" ]; then
            echo "ERROR: EC2 instance is not running (current state: $EC2_STATUS)"
            exit 1
          fi

          echo "Creating JSON file for SSM directory setup..."
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

          echo "Sending SSM command to prepare directories..."
          WRITE_CMD_ID=$(aws ssm send-command \
            --document-name "AWS-RunShellScript" \
            --instance-ids "${DEPLOY_EC2_INSTANCE_ID}" \
            --comment "prepare dirs" \
            --parameters file://ssm-params.json \
            --output text \
            --query "Command.CommandId")

          echo "Waiting for directory setup to complete (Command ID: $WRITE_CMD_ID)..."
          aws ssm wait command-executed --command-id "$WRITE_CMD_ID" --instance-id "${DEPLOY_EC2_INSTANCE_ID}" || echo "Wait may time out; continuing..."

          FILE_RESULT=$(aws ssm get-command-invocation \
            --command-id "$WRITE_CMD_ID" \
            --instance-id "${DEPLOY_EC2_INSTANCE_ID}" \
            --query "Status" \
            --output text)
          echo "Dir prep status: $FILE_RESULT"
          if [ "$FILE_RESULT" != "Success" ]; then
            echo "ERROR: Directory prep failed."
            exit 1
          fi

          echo "Building remote deploy script (deploy.sh)..."
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

# Docker 데몬 보장 (systemd 없으면 무시)
if command -v systemctl >/dev/null 2>&1; then
  systemctl is-active docker >/dev/null 2>&1 || systemctl start docker || true
fi

# ECR 로그인 & 이미지 pull
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com"
docker pull "$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/community-portfolio:${IMAGE_TAG}" || true
docker rm -f community-app || true

# ---- SSM → 환경 변수 로드 ----
DB_URL=$(aws ssm get-parameter --name "$SSM_PREFIX/db/url" --with-decryption --query 'Parameter.Value' --output text)
DB_USER=$(aws ssm get-parameter --name "$SSM_PREFIX/db/user" --with-decryption --query 'Parameter.Value' --output text)
DB_PASS=$(aws ssm get-parameter --name "$SSM_PREFIX/db/pass" --with-decryption --query 'Parameter.Value' --output text)

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
PUBLIC_BASE_URL=$(aws ssm get-parameter --name "$SSM_PREFIX/public-base-url" --with-decryption --query 'Parameter.Value' --output text 2>/dev/null || echo "")
S3_BUCKET=$(aws ssm get-parameter --name "$SSM_PREFIX/s3/bucket" --with-decryption --query 'Parameter.Value' --output text 2>/dev/null || echo "")

ADMIN_EMAIL=$(aws ssm get-parameter --name "$SSM_PREFIX/admin/email" --with-decryption --query 'Parameter.Value' --output text 2>/dev/null || echo "")
ADMIN_USERNAME=$(aws ssm get-parameter --name "$SSM_PREFIX/admin/username" --with-decryption --query 'Parameter.Value' --output text 2>/dev/null || echo "")
ADMIN_PASSWORD_HASH=$(aws ssm get-parameter --name "$SSM_PREFIX/admin/password-hash" --with-decryption --query 'Parameter.Value' --output text 2>/dev/null || echo "")

# DB 존재 보장 (최초 배포 대비)
db_url_no_prefix="${DB_URL#jdbc:mysql://}"
hostport_and_path="${db_url_no_prefix%%/*}"
DB_HOST="${hostport_and_path%%:*}"
DB_PORT="${hostport_and_path##*:}"
[ "$DB_PORT" = "$hostport_and_path" ] || [ -z "$DB_PORT" ] && DB_PORT="3306"
path_after_slash="${db_url_no_prefix#*/}"
DB_NAME=$(printf '%s\n' "$path_after_slash" | cut -d'?' -f1)
if [ -n "$DB_HOST" ] && [ -n "$DB_NAME" ]; then
  echo "Ensuring database '$DB_NAME' exists on $DB_HOST:$DB_PORT..."
  docker run --rm mysql:8 \
    mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" \
      -e "CREATE DATABASE IF NOT EXISTS $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;" \
  || echo "WARN: Could not ensure database exists (non-fatal)."
fi

# 안전성 체크
if [ "${ALLOWED_ORIGINS}" = "*" ]; then
  echo "ERROR: ALLOWED_ORIGINS must not be '*' in production." >&2
  exit 1
fi
if [ -n "$ADMIN_EMAIL" ] || [ -n "$ADMIN_USERNAME" ] || [ -n "$ADMIN_PASSWORD_HASH" ]; then
  if [ -z "$ADMIN_EMAIL" ] || [ -z "$ADMIN_USERNAME" ] || [ -z "$ADMIN_PASSWORD_HASH" ]; then
    echo "ERROR: ADMIN_* parameters are partially provided. Require all of ADMIN_EMAIL, ADMIN_USERNAME, ADMIN_PASSWORD_HASH." >&2
    exit 1
  fi
fi

# .env 생성 - 환경 변수 직접 지정
cat > .env <<EOF
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
PUBLIC_BASE_URL=$PUBLIC_BASE_URL
S3_BUCKET=$S3_BUCKET
AWS_REGION=$REGION
ACCOUNT_ID=$ACCOUNT_ID
SERVER_PORT=8080
ADMIN_EMAIL=$ADMIN_EMAIL
ADMIN_USERNAME=$ADMIN_USERNAME
ADMIN_PASSWORD_HASH=$ADMIN_PASSWORD_HASH
EOF

# .env 파일 내용 확인 (디버깅용)
echo "Created .env file with the following contents:"
cat .env

# compose 생성 및 기동
cat > docker-compose.yml <<YML
version: '3.8'
services:
  app:
    image: ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/community-portfolio:${IMAGE_TAG}
    container_name: community-app
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: "prod"
      DB_URL: "${DB_URL}"
      DB_USERNAME: "${DB_USER}"
      DB_PASSWORD: "${DB_PASS}"
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
      SERVER_PORT: "8080"
      ADMIN_EMAIL: "${ADMIN_EMAIL}"
      ADMIN_USERNAME: "${ADMIN_USERNAME}"
      ADMIN_PASSWORD_HASH: "${ADMIN_PASSWORD_HASH}"
    volumes:
      - ./uploads:/app/uploads
YML

docker-compose pull || true
echo "Starting the application container with docker-compose..."
# .env 파일을 명시적으로 지정하여 실행
docker-compose --env-file .env up -d

# 헬스 체크 로직 개선
echo "Waiting for health check (up to ~90s)..."
HEALTH_CHECK_PASSED=false
for i in $(seq 1 30); do
  echo "Health check attempt $i/30..."
  if curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
    echo "Health check passed successfully!"
    HEALTH_CHECK_PASSED=true
    break
  fi
  sleep 3
done

if [ "$HEALTH_CHECK_PASSED" != "true" ]; then
  echo "Health check timed out. Showing container logs..."
  docker-compose logs --no-color app | tail -n 200 || true
  echo "Container status:"
  docker ps -a | grep community-app || true
  exit 1
fi

echo "Application successfully deployed and running!"
EOS

          # 자리표시자 치환
          sed -i "s|__ACCOUNT_ID__|${ACCOUNT_ID}|g; s|__AWS_REGION__|${AWS_REGION}|g; s|__SSM_PREFIX__|${SSM_PREFIX}|g; s|__IMAGE_TAG__|${IMAGE_CHANNEL_TAG}|g" deploy-remote.sh

          # 원격 실행을 위해 base64로 전송
          if base64 --help 2>&1 | grep -q -- "-w"; then
            DEPLOY_B64=$(base64 -w0 deploy-remote.sh)
          else
            DEPLOY_B64=$(base64 deploy-remote.sh | tr -d '\\n')
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
          POLL_MAX=120
          POLL_INTERVAL=5
          DEPLOY_RESULT="Pending"
          for i in $(seq 1 $POLL_MAX); do
            DEPLOY_RESULT=$(aws ssm get-command-invocation \
              --command-id "$DEPLOY_CMD_ID" \
              --instance-id "${DEPLOY_EC2_INSTANCE_ID}" \
              --query "Status" \
              --output text 2>/dev/null || echo "Unknown")
            echo "Deployment status: $DEPLOY_RESULT ($i/$POLL_MAX)"
            case "$DEPLOY_RESULT" in
              Success) break ;;
              Failed|Cancelled|TimedOut) break ;;
              InProgress|Pending|Delayed|Cancelling|Unknown) sleep $POLL_INTERVAL ;;
              *) sleep $POLL_INTERVAL ;;
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
      script {
        try { cleanWs() }
        catch (Exception e) { echo 'cleanWs() not available, falling back to deleteDir()'; deleteDir() }
      }
    }
    failure {
      echo 'Build or deployment failed. Check logs and test reports.'
    }
    success {
      script {
        if (env.DEPLOY_ENABLED == 'true') {
          echo 'Build and deployment succeeded. Application is now running on EC2.'
        } else {
          echo 'Build succeeded (CI only). 배포를 원하면 DEPLOY 체크박스를 선택하세요.'
        }
      }
    }
  }
}
