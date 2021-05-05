def call() {
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