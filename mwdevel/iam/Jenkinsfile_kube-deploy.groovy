#!groovy
// name iam-kube-deploy

properties([
  buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5')),
  parameters([
    choice(name: 'ENVIRONMENT', choices: 'STAGING\nPRODUCTION', description: ''),
    string(name: 'IAM_IMAGE',   defaultValue: 'cloud-vm128.cloud.cnaf.infn.it/indigoiam/iam-login-service:latest', description: ''),
  ]),
  //  pipelineTriggers([cron('@daily')]),
])


def repo = 'git@baltig.infn.it:caberletti/kube_deployments.git'
def directory = 'iam/staging/iam-login-service'
def namespace = 'staging'

if ("PRODUCTION".equals("${params.PRODUCTION}")) {
  repo = "git@baltig.infn.it:mw-devel/iam-test.indigo-datacloud.eu.git"
  directory = 'iam-login-service'
  namespace = 'indigo'
}

node("kubectl"){
  stage('Deploy') {
    git "${repo}"

    dir("${directory}") {
      withEnv([
        "IAM_IMAGE=${params.IAM_IMAGE}",
        "NAMESPACE=${namespace}",
      ]){
        try {
          sh "kubectl apply -f iam-db.secret.yaml -f iam-google.secret.yaml -f iam-ssl.secret.yaml --namespace=${NAMESPACE}"
          sh "kubectl set image -f iam.deploy.yaml iam-login-service=${IAM_IMAGE} --namespace=${NAMESPACE} --local -o yaml > iam.deploy.yaml.new"
          sh "mv iam.deploy.yaml.new iam.deploy.yaml"
          sh "cat iam.deploy.yaml"
          sh "kubectl apply -f iam.deploy.yaml --namespace=${NAMESPACE}"
          sh "kubectl apply -f iam.svc.yaml --namespace=${NAMESPACE}"
          sh "kubectl apply -f iam.ingress.yaml --namespace=${NAMESPACE}"

          timeout(time: 5, unit: 'MINUTES') {  sh "kubectl rollout status deploy/iam --namespace=${NAMESPACE} | grep -q 'successfully rolled out'" }
        }catch(error){
          withEnv(["NAMESPACE=${namespace}",]){   sh "kubectl rollout undo deployment/iam --namespace=${NAMESPACE}"   }
          currentBuild.result = 'FAILURE'
        }
      }
    }
  }
}
