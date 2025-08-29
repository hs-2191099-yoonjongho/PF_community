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
    booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: 'Skip tests for faster builds (useful if DB not ready)')
    string(name: 'AWS_ACCOUNT_ID', defaultValue: '', description: 'AWS 계정 ID')
    string(name: 'AWS_REGION', defaultValue: 'ap-northeast-2', description: 'AWS 리전')
    string(name: 'EC2_INSTANCE_ID', defaultValue: '', description: 'EC2 인스턴스 ID')
    string(name: 'AWS_CREDENTIALS_ID', defaultValue: 'aws-jenkins-accesskey', description: 'Jenkins에 설정된 AWS 자격증명 ID')
    string(name: 'DEPLOY_ROLE_ARN', defaultValue: '', description: 'AWS IAM 배포 역할 ARN')
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
            branches: [[name: '*/branch(v5)']],                 // 괄호가 있는 브랜치명은 이 패턴이 안전
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
      - "8082:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
    volumes:
      - ./uploads:/app/uploads
EOF

          # Base64로 인코딩하여 단일 라인으로 변환
          echo "Converting docker-compose to base64..."
          if base64 --help 2>&1 | grep -q -- "-w"; then
            COMPOSE_B64=$(base64 -w0 docker-compose-remote.yml)
          else
            COMPOSE_B64=$(base64 docker-compose-remote.yml | tr -d '\\n')
          fi

          # SSM 명령 파라미터 JSON 파일 생성
          echo "Creating SSM parameter JSON file..."
          cat > ssm-write-compose.json <<EOF
{
  "commands": [
    "set -e",
    "mkdir -p /opt/community-portfolio",
    "echo ${COMPOSE_B64} | base64 -d > /opt/community-portfolio/docker-compose.yml"
  ]
}
EOF

          # docker-compose.yml 파일 생성을 위한 SSM 명령 실행
          echo "Sending SSM command to write docker-compose.yml..."
          COMPOSE_CMD_ID=$(aws ssm send-command \
            --document-name "AWS-RunShellScript" \
            --instance-ids "${EC2_INSTANCE_ID}" \
            --comment "Write compose file" \
            --parameters file://ssm-write-compose.json \
            --query "Command.CommandId" --output text)

          echo "Waiting for compose file creation to complete..."
          aws ssm wait command-executed \
            --command-id "$COMPOSE_CMD_ID" --instance-id "${EC2_INSTANCE_ID}" || true

          # 배포 명령어 JSON 파일 생성
          echo "Creating deployment commands JSON..."
          cat > ssm-deploy.json <<'EOF'
{
  "commands": [
    "set -e",
    "cd /opt/community-portfolio",
    "mkdir -p uploads",
    "chmod 777 /opt/community-portfolio /opt/community-portfolio/uploads || true",
    "ls -la",
    "aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin 950756301515.dkr.ecr.ap-northeast-2.amazonaws.com",
    "docker compose pull app || echo 'Pull failed but continuing'",
    "docker compose up -d app || (echo 'Docker compose failed:' && cat docker-compose.yml && exit 1)",
    "docker compose ps",
    "sleep 10",
    "curl -fsS http://localhost:8082/actuator/health || (docker logs --tail=200 community-app && exit 1)"
  ]
}
EOF

          # 배포 실행
          echo "Deploying via SSM and docker compose..."
          DEPLOY_CMD_ID=$(aws ssm send-command \
            --document-name "AWS-RunShellScript" \
            --instance-ids "${EC2_INSTANCE_ID}" \
            --comment "Deploy application" \
            --parameters file://ssm-deploy.json \
            --query "Command.CommandId" --output text)

          echo "Waiting for deployment to complete: $DEPLOY_CMD_ID"
          aws ssm wait command-executed \
            --command-id "$DEPLOY_CMD_ID" \
            --instance-id "${EC2_INSTANCE_ID}" || true
          
          echo "Getting deployment command status..."
          aws ssm list-commands \
            --command-id "$DEPLOY_CMD_ID" \
            --query 'Commands[0].Status' \
            --output text

          echo "Deployment results:"
          aws ssm get-command-invocation \
            --command-id "$DEPLOY_CMD_ID" \
            --instance-id "${EC2_INSTANCE_ID}" \
            --query '{Status:Status,StdOut:StandardOutputContent,StdErr:StandardErrorContent}' \
            --output json || echo "Failed to get deployment results"
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
