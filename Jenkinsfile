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
    booleanParam(name: 'SKIP_TESTS', defaultValue: true, description: 'Skip tests for faster builds (useful if DB not ready)')
    string(name: 'AWS_ACCOUNT_ID', defaultValue: '', description: 'AWS 계정 ID')
    string(name: 'AWS_REGION', defaultValue: 'ap-northeast-2', description: 'AWS 리전')
    string(name: 'EC2_INSTANCE_ID', defaultValue: '', description: 'EC2 인스턴스 ID')
    string(name: 'AWS_CREDENTIALS_ID', defaultValue: 'aws-jenkins-accesskey', description: 'Jenkins에 설정된 AWS 자격증명 ID')
    string(name: 'DEPLOY_ROLE_ARN', defaultValue: '', description: 'AWS IAM 배포 역할 ARN')
  string(name: 'SSM_PREFIX', defaultValue: '/community-portfolio/dev', description: 'SSM 파라미터 프리픽스 (예: /community-portfolio/dev 또는 /community-portfolio/prod)')
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
    EC2_INSTANCE_ID = "${params.EC2_INSTANCE_ID}"
    DEPLOY_ROLE_ARN = "${params.DEPLOY_ROLE_ARN}"
  SSM_PREFIX = "${params.SSM_PREFIX}"
  }

  stages {
    stage('Checkout') {
      steps {
        script {
          def remote = [url: 'https://github.com/hs-2191099-yoonjongho/PF_community.git']
          if (params.GIT_CREDENTIALS_ID?.trim()) {
            remote.credentialsId = params.GIT_CREDENTIALS_ID.trim()
          }
          checkout([
            $class: 'GitSCM',
            branches: [[name: '*/branch(v6)']],                 // 괄호가 있는 브랜치명은 이 패턴이 안전
            userRemoteConfigs: [remote],
            extensions: [[
              $class: 'CloneOption', shallow: true, depth: 1, noTags: true, timeout: 20
            ]]
          ])
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
          def buildCmd = params.SKIP_TESTS ? './gradlew --no-daemon clean assemble -x test' : './gradlew --no-daemon clean build'
          sh buildCmd
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
      steps {
        withCredentials([[$class:'AmazonWebServicesCredentialsBinding', credentialsId: params.AWS_CREDENTIALS_ID]]) {
          sh 'aws sts get-caller-identity'
        }
      }
    }
    
    stage('AssumeRole') {
      when { 
        expression { params.DEPLOY_ROLE_ARN?.trim() && params.AWS_ACCOUNT_ID?.trim() && params.EC2_INSTANCE_ID?.trim() }
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
      when { expression { return params.DEPLOY_ROLE_ARN?.trim() && params.AWS_ACCOUNT_ID?.trim() && params.EC2_INSTANCE_ID?.trim() } }
      steps {
        sh '''
          set -e
          . ./aws_env_export

          echo "Validating temporary credentials..."
          aws sts get-caller-identity

          echo "Logging in to ECR..."
          aws ecr get-login-password --region ${AWS_REGION} | \
            docker login --username AWS --password-stdin ${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com

          REPO=${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/community-portfolio
          SHA=$(git rev-parse --short HEAD)

          docker build -t $REPO:latest -t $REPO:$SHA .
          docker push $REPO:latest
          docker push $REPO:$SHA
        '''
      }
    }
    
    stage('Deploy to EC2') {
      when { expression { params.DEPLOY_ROLE_ARN?.trim() && params.AWS_ACCOUNT_ID?.trim() && params.EC2_INSTANCE_ID?.trim() } }
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
          EC2_STATUS=$(aws ec2 describe-instances --instance-ids ${EC2_INSTANCE_ID} --query 'Reservations[0].Instances[0].State.Name' --output text)
          echo "EC2 instance status: $EC2_STATUS"
          
          if [ "$EC2_STATUS" != "running" ]; then
            echo "ERROR: EC2 instance is not running (current state: $EC2_STATUS)"
            exit 1
          fi
          
          # docker-compose.yml 파일 생성
          echo "Preparing docker-compose.yml content..."
          cat > docker-compose-remote.yml <<EOF
version: '3.8'

services:
  app:
    container_name: community-app
    image: ${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/community-portfolio:latest
    restart: always
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
    volumes:
      - ./uploads:/app/uploads
EOF

          # docker-compose.yml 파일을 Base64로 인코딩
          echo "Converting docker-compose.yml to base64..."
          if base64 --help 2>&1 | grep -q -- "-w"; then
            # GNU base64 (Linux)
            COMPOSE_B64=$(base64 -w0 docker-compose-remote.yml)
          else
            # BSD base64 (macOS)
            COMPOSE_B64=$(base64 docker-compose-remote.yml | tr -d '\\n')
          fi

          # 디렉토리 생성 및 파일 쓰기 명령을 위한 JSON 파일 생성
          echo "Creating JSON file for SSM command..."
          cat > ssm-params.json <<EOF
{
  "commands": [
    "mkdir -p /opt/community-portfolio",
    "mkdir -p /opt/community-portfolio/uploads",
    "chmod -R 777 /opt/community-portfolio/uploads",
    "echo '${COMPOSE_B64}' | base64 -d > /opt/community-portfolio/docker-compose.yml",
    "ls -la /opt/community-portfolio"
  ]
}
EOF

          # SSM 명령 실행
          echo "Sending SSM command to create docker-compose.yml..."
          WRITE_CMD_ID=$(aws ssm send-command \
            --document-name "AWS-RunShellScript" \
            --instance-ids "${EC2_INSTANCE_ID}" \
            --comment "Write docker-compose.yml" \
            --parameters file://ssm-params.json \
            --output text \
            --query "Command.CommandId")

          echo "Waiting for file creation to complete (Command ID: $WRITE_CMD_ID)..."
          aws ssm wait command-executed \
            --command-id "$WRITE_CMD_ID" \
            --instance-id "${EC2_INSTANCE_ID}" || echo "Wait command timed out, but continuing..."

          # 명령 결과 확인
          echo "Checking file creation results..."
          FILE_RESULT=$(aws ssm get-command-invocation \
            --command-id "$WRITE_CMD_ID" \
            --instance-id "${EC2_INSTANCE_ID}" \
            --query "Status" \
            --output text)
            
          echo "File creation status: $FILE_RESULT"
          
          if [ "$FILE_RESULT" != "Success" ]; then
            echo "Error creating docker-compose.yml file:"
            aws ssm get-command-invocation \
              --command-id "$WRITE_CMD_ID" \
              --instance-id "${EC2_INSTANCE_ID}" \
              --query "StandardErrorContent" \
              --output text
            exit 1
          fi

          # 애플리케이션 배포 명령 JSON 생성
          echo "Building remote deploy script (deploy.sh)..."
          # 1) 원격에서 실행할 배포 스크립트를 로컬에서 생성 (변수 확장 방지를 위해 리터럴 heredoc 사용)
          cat > deploy-remote.sh <<'EOS'
#!/usr/bin/env bash
set -euo pipefail

REGION="__AWS_REGION__"
ACCOUNT_ID="__ACCOUNT_ID__"
SSM_PREFIX="__SSM_PREFIX__"

cd /opt/community-portfolio
mkdir -p uploads
chmod -R 777 uploads
chmod 777 .

# Docker 실행 확인 및 시작 (systemd가 없으면 무시)
if command -v systemctl >/dev/null 2>&1; then
  systemctl is-active docker >/dev/null 2>&1 || systemctl start docker || true
fi

# ECR 로그인 및 최신 이미지 pull
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com"
docker pull "$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/community-portfolio:latest" || true
docker rm -f community-app || true

# --- SSM → 환경변수 로드 ---
DB_URL=$(aws ssm get-parameter --name "$SSM_PREFIX/db/url" --with-decryption --query 'Parameter.Value' --output text)
DB_USER=$(aws ssm get-parameter --name "$SSM_PREFIX/db/user" --with-decryption --query 'Parameter.Value' --output text)
DB_PASS=$(aws ssm get-parameter --name "$SSM_PREFIX/db/pass" --with-decryption --query 'Parameter.Value' --output text)

# EC2 인스턴스의 퍼블릭 IP 가져오기
EC2_PUBLIC_IP=$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4)
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

# 컨테이너 실행
docker run -d --restart=always --name community-app -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL="$DB_URL" \
  -e DB_USERNAME="$DB_USER" \
  -e DB_PASSWORD="$DB_PASS" \
  -e JWT_SECRET="$JWT_SECRET" \
  -e JWT_ACCESS_EXP_MS="$JWT_ACCESS_EXP_MS" \
  -e JWT_ISSUER="$JWT_ISSUER" \
  -e REFRESH_EXP_MS="$REFRESH_EXP_MS" \
  -e REFRESH_COOKIE_NAME="$REFRESH_COOKIE_NAME" \
  -e REFRESH_COOKIE_PATH="$REFRESH_COOKIE_PATH" \
  -e REFRESH_COOKIE_SECURE="$REFRESH_COOKIE_SECURE" \
  -e REFRESH_COOKIE_SAME_SITE="$REFRESH_COOKIE_SAME_SITE" \
  -e REFRESH_COOKIE_DOMAIN="${REFRESH_COOKIE_DOMAIN:-$EC2_PUBLIC_IP}" \
  -e ALLOWED_ORIGINS="$ALLOWED_ORIGINS" \
  -e PUBLIC_BASE_URL="http://$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4):8080/files" \
  -e S3_BUCKET="$S3_BUCKET" \
  -e AWS_REGION="$REGION" \
  -v /opt/community-portfolio/uploads:/app/uploads \
  "$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/community-portfolio:latest"

echo "Container started. Waiting for health (up to ~90s)..."

# Retry health check up to 30 times (3s interval)
for i in $(seq 1 30); do
  # If container died, fail early with logs
  if ! docker ps --format '{{.Names}}' | grep -q '^community-app$'; then
    echo "Container is not running anymore. Showing logs..."
    docker logs community-app || true
    exit 1
  fi

            # Probe health endpoint
            if curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
    echo "Health check passed."
    exit 0
  fi

  echo "Health not ready yet ($i/30). Sleeping 3s..."
  sleep 3
done

echo "Health check timed out. Showing logs..."
docker logs community-app || true
exit 1
EOS

          # 2) 자리표시자 치환 (안전한 구분자 사용)
          sed -i "s|__ACCOUNT_ID__|${ACCOUNT_ID}|g; s|__AWS_REGION__|${AWS_REGION}|g; s|__SSM_PREFIX__|${SSM_PREFIX}|g" deploy-remote.sh

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
    "chmod -R 777 /opt/community-portfolio/uploads",
    "echo '${DEPLOY_B64}' | base64 -d > /opt/community-portfolio/deploy.sh",
    "chmod +x /opt/community-portfolio/deploy.sh",
    "/opt/community-portfolio/deploy.sh"
  ]
}
EOF

          echo "Deploying application via SSM..."
          DEPLOY_CMD_ID=$(aws ssm send-command \
            --document-name "AWS-RunShellScript" \
            --instance-ids "${EC2_INSTANCE_ID}" \
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
              --instance-id "${EC2_INSTANCE_ID}" \
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
              --instance-id "${EC2_INSTANCE_ID}" \
              --query "{Error:StandardErrorContent, Output:StandardOutputContent}" \
              --output json || true
            exit 1
          fi

          echo "Deployment succeeded! Application should be running at http://<EC2-PUBLIC-IP>:8080"
          aws ssm get-command-invocation \
            --command-id "$DEPLOY_CMD_ID" \
            --instance-id "${EC2_INSTANCE_ID}" \
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
