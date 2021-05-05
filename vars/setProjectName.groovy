def call(script) {
  script {
    allJob = script.env.JOB_NAME.tokenize('/') as String[];
    //env.PROJECT_NAME = allJob[0];
    name = allJob[0];
    echo "Project name: ${name}"
  }
}