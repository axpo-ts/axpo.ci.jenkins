def setProjectName() {
  allJob = env.JOB_NAME.tokenize('/') as String[];
  env.PROJECT_NAME = allJob[0];
  echo "Project name: ${env.PROJECT_NAME}"
}

def makeVersion() {
  env.IGNORE_NORMALISATION_GIT_HEAD_MOVE = 1
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
  powershell "dotnet restore"
  powershell "dotnet clean . -c Release"
  powershell "dotnet build . -c Release /p:Version=${env.GIT_VERSION}"
}

def dotnetPack(Map args) {
  outDir = "./artefacts/"
  powershell "Remove-Item ${outDir} -Recurse -ErrorAction Ignore"
  powershell "New-Item -ItemType Directory -Force -Path ${outDir}"
  noBuild = (args.get('forceBuild', 'false')) ? "" : "--no-build"
  powershell "dotnet pack . -c Release --include-symbols --include-source ${noBuild} --output ${outDir} /p:Version=${env.GIT_VERSION}"
}

def dotnetPublish(Map args) {
  powershell "dotnet publish ${args.project} --no-build -c Release -o octo_upload"
}

def publishAllowed() {
  isForced = env.FORCE_PUBLISH == 'true'
  isMaster = env.BRANCH_NAME == 'master'
  isPR = env.CHANGE_ID != null
  if (isPR) {
    echo "no publish allowed from change requests"
    return false
  } else if (isForced) {
    echo "publishing forced by parameter"
    return true
  } else if (isMaster) {
    echo "automatically publishig from master"
    return true
  } else {
    return false
  }
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
  directory = args.get('directory', 'octo_upload')
  zip zipFile: "${filename}", archive: false, dir: "${directory}", overwrite: true
  echo "upload ${filename} to octopus ${env.OCTOPUS_SERVER}."
  withCredentials([string(credentialsId: 'OctopusAPIKey', variable: 'APIKey')]) {
    powershell("${tool('Octo CLI')} push --package ${filename} --replace-existing --server ${env.OCTOPUS_SERVER} --apiKey ${APIKey}")
  }
}

def pushTag() {
  echo "create new git tag"
  powershell "git tag -f ${env.GIT_VERSION}"
  gitUrlBase = "${GIT_URL}".split("//")[1]
  withCredentials([usernamePassword(credentialsId: 'c2457393-c808-4b22-a3a6-26316ad4e562', usernameVariable: 'GIT_USR', passwordVariable: 'GIT_PWD')]) {
    powershell("git push https://${GIT_USR}:${GIT_PWD}@${gitUrlBase} :refs/tags/${env.GIT_VERSION}")
    powershell("git push https://${GIT_USR}:${GIT_PWD}@${gitUrlBase} ${env.GIT_VERSION}")
  }
}

return this
