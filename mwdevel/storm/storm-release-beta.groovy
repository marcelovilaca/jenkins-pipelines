pipeline {
  agent {
    label 'docker'
  }

  parameters {
    string(name: 'PKG_STORM_BRANCH', defaultValue: 'v1.11.18', description: 'The branch of the pkg.storm repo')
  }

  options {
    timestamps()
    ansiColor('xterm')
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }
    
  stages {
      
    stage('build packages'){
      steps{
        build "pkg.storm/${params.PKG_STORM_BRANCH}"
      }
    }
      
    stage('update beta repos'){
      steps {
        build 'release.storm/beta'
      }
    }
  }

  post {
    failure {
      slackSend channel: '#storm', color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
    }
    
    changed {
      script{
        if('SUCCESS'.equals(currentBuild.currentResult)) {
          slackSend channel: '#storm', color: 'good', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Back to normal (<${env.BUILD_URL}|Open>)"
        }
      }
    }
  }
}
