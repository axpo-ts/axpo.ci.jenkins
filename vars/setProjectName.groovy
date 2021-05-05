def call() {
  script {
    allJob = env.JOB_NAME.tokenize('/') as String[];
    //env.PROJECT_NAME = allJob[0];
    name = allJob[0];
    echo "Project name: ${name}"
  }
}