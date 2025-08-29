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
            branches: [[name: '*/branch(v4)']],                 // 괄호가 있는 브랜치명은 이 패턴이 안전
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
        withCredentials([[$class:'AmazonWebServicesCredentialsBinding', credentialsId:'aws-jenkins-accesskey']]) {
          sh 'aws sts get-caller-identity'
        }
      }
    }
    
    stage('AssumeRole') {
      when { 
        expression { 
          return params.DEPLOY_ROLE_ARN?.trim() && params.AWS_ACCOUNT_ID?.trim() && params.EC2_INSTANCE_ID?.trim()
        }
      }
      steps {
        withCredentials([[$class:'AmazonWebServicesCredentialsBinding', credentialsId:'aws-jenkins-accesskey']]) {
          sh '''
            set -e
            
            # 임시 변수로 저장 (로그에 출력 최소화)
            AK=$(aws sts assume-role \
              --role-arn ${DEPLOY_ROLE_ARN} \
              --role-session-name jenkins-deploy \
              --duration-seconds 3600 \
              --query 'Credentials.AccessKeyId' \
              --output text)
              
            SK=$(aws sts assume-role \
              --role-arn ${DEPLOY_ROLE_ARN} \
              --role-session-name jenkins-deploy \
              --duration-seconds 3600 \
              --query 'Credentials.SecretAccessKey' \
              --output text)
              
            ST=$(aws sts assume-role \
              --role-arn ${DEPLOY_ROLE_ARN} \
              --role-session-name jenkins-deploy \
              --duration-seconds 3600 \
              --query 'Credentials.SessionToken' \
              --output text)

            # 자격 증명 파일 생성 (쉘 안전하게)
            cat > aws_env_export << EOF
export AWS_ACCESS_KEY_ID=$AK
export AWS_SECRET_ACCESS_KEY=$SK
export AWS_SESSION_TOKEN=$ST
export AWS_DEFAULT_REGION=${AWS_REGION}
EOF
          '''
        }
      }
    }
    
    stage('Build & Push Docker Image') {
      when { 
        expression { 
          return params.DEPLOY_ROLE_ARN?.trim() && params.AWS_ACCOUNT_ID?.trim() && params.EC2_INSTANCE_ID?.trim()
        }
      }
      steps {
        sh '''
          set -e
          . ./aws_env_export
          
          # 자격 증명 확인
          aws sts get-caller-identity
          
          # 레지스트리 로그인
          aws ecr get-login-password --region ${AWS_REGION} | \
            docker login --username AWS --password-stdin ${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com
          
          # 이미지 빌드 및 태그
          REPO=${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/community-portfolio
          SHA=$(git rev-parse --short HEAD)
          
          docker build -t $REPO:latest -t $REPO:$SHA .
          docker push $REPO:latest
          docker push $REPO:$SHA
          echo "Pushed tags: latest, $SHA"
        '''
      }
    }
    
    stage('Deploy to EC2') {
      when { 
        expression { 
          return params.DEPLOY_ROLE_ARN?.trim() && params.AWS_ACCOUNT_ID?.trim() && params.EC2_INSTANCE_ID?.trim()
        }
      }
      steps {
        sh '''
          set -e
          . ./aws_env_export
          
          # 자격 증명 확인
          aws sts get-caller-identity
          
          # EC2 배포
          aws ssm send-command \
            --document-name "AWS-RunShellScript" \
            --instance-ids "${EC2_INSTANCE_ID}" \
            --parameters commands="cd /opt/community-portfolio && \
                                 aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com && \
                                 docker compose pull app && \
                                 docker compose up -d app" \
            --region ${AWS_REGION} \
            --comment "Deploy community-portfolio app"
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
