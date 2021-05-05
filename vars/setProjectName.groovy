def call() {
  script {
    allJob = env.JOB_NAME.tokenize('/') as String[];
    env.PROJECT_NAME = allJob[0];
  }
}