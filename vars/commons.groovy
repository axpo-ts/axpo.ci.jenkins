def setProjectName() {
  allJob = env.JOB_NAME.tokenize('/') as String[];
  env.PROJECT_NAME = allJob[0];
  echo "Project name: ${env.PROJECT_NAME}"
}

def makeVersion() {
  // generate semantic version using gitversion
  powershell "dotnet-gitversion /output buildserver"
  // inject gitversion props as environment variables
  readFile('gitversion.properties').split("\r\n").each { line ->
    el = line.split("=")
    env."${el[0]}" = (el.size() > 1) ? "${el[1]}" : ""
  }
  // going for the nuget-version format
  env.GIT_VERSION = "${env.GitVersion_NugetVersion}"
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
  filename = "${args.name}.${env.GIT_VERSION}.zip"
  zip zipFile: "${filename}", archive: false, dir: 'octo_upload', overwrite: true
  echo "upload ${filename} to octopus ${env.OCTOPUS_SERVER}."
  withCredentials([string(credentialsId: 'OctopusAPIKey', variable: 'APIKey')]) {
    powershell "${tool('Octo CLI')} push --package ${filename} --replace-existing --server ${env.OCTOPUS_SERVER} --apiKey ${APIKey}"
  }
}

def pushTag() {
  echo "create and push new git tag"
  powershell "git tag -f ${env.GIT_VERSION}"
  gitUrlBase = "${GIT_URL}".split("//")[1]
  echo "${gitUrlBase}"
  withCredentials([usernamePassword(credentialsId: 'c2457393-c808-4b22-a3a6-26316ad4e562', usernameVariable: 'GIT_USR', passwordVariable: 'GIT_PWD')]) {
    powershell("echo https://${GIT_USR}:${GIT_PWD}@${gitUrlBase}")
    //git push $gitUrlWithCredentials :refs/tags/''' + "${env.GIT_VERSION}" + '''
    //git push $gitUrlWithCredentials ''' + "${env.GIT_VERSION}"
  }
}

return this
