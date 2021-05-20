def setProjectName() {
  allJob = env.JOB_NAME.tokenize('/') as String[];
  env.PROJECT_NAME = allJob[0];
  echo "Project name: ${env.PROJECT_NAME}"
}

def makeVersion() {
  if(env.CHANGE_ID) {
    // this is a workaround: gitversion is not able to come up with a version inside a PR in jenkins
    echo "processing Pull Request: dummy semantic version."
    env.GIT_VERSION = "1.0.0-${env.GIT_BRANCH}"
  } else {
    // generate semantic version using gitversion
    powershell "dotnet-gitversion /output buildserver"
    // inject gitversion props as environment variables
    readFile('gitversion.properties').split("\r\n").each { line ->
      el = line.split("=")
      env."${el[0]}" = (el.size() > 1) ? "${el[1]}" : ""
    }
    // going for the nuget-version format
    env.GIT_VERSION = "${env.GitVersion_NugetVersion}"
  }
  echo "Version: ${env.GIT_VERSION}"
}

def dotnetBuild() {
  powershell "dotnet --version"
  powershell "dotnet clean"
  powershell "dotnet restore"
  powershell "dotnet build . -c Release /p:Version=${env.GIT_VERSION}"
}

def dotnetPack() {
  outDir = "./artefacts/"
  powershell "Remove-Item ${outDir} -Recurse -ErrorAction Ignore"
  powershell "New-Item -ItemType Directory -Force -Path ${outDir}"
  powershell "dotnet pack . -c Release --include-symbols --include-source --no-build --output ${outDir} /p:Version=${env.GIT_VERSION}"
}

def dotnetPublish(Map args) {
  powershell "dotnet publish ${args.project} --no-build -c Release -o octo_upload"
}

def jfrogUpload(Map args) {
  artifactoryBuildNumber = "${env.GIT_BRANCH}-${env.BUILD_ID}"
  rtServer (
    id: 'ARTIFACTORY_SERVER',
    url: "${env.ARTIFACTORY_SERVER}",
    credentialsId: 'svc-jenkins'
  )
  echo "upload artefacts to artifactory ${env.ARTIFACTORY_SERVER} under ${env.PROJECT_NAME} and build number ${artifactoryBuildNumber}."
  rtUpload (
    serverId: 'ARTIFACTORY_SERVER',
    buildName: "${env.PROJECT_NAME}",
    buildNumber: "${artifactoryBuildNumber}",
    spec: '''{
      "files": [
        {
          "pattern": "artefacts/*.nupkg",
          "target": "''' + "${args.target}" + '''"
        }
      ]
    }'''
  )
  rtPublishBuildInfo (
    serverId: 'ARTIFACTORY_SERVER',
    buildName: "${env.PROJECT_NAME}",
    buildNumber: "${artifactoryBuildNumber}"
  )
}

def octoUpload(Map args) {
  zip zipFile: "${args.zipfile}", archive: false, dir: 'octo_upload', overwrite: true
  echo "upload ${args.zipfile} to octopus ${env.OCTOPUS_SERVER}."
  withCredentials([string(credentialsId: 'OctopusAPIKey', variable: 'APIKey')]) {
    powershell "${tool('Octo CLI')} push --package ${args.zipfile} --replace-existing --server ${env.OCTOPUS_SERVER} --apiKey ${APIKey}"
  }
}

return this
