def getEnvProfile(branchName) {
    if("master".equals(branchName)) {
        return "prod";
    } else if ("test".equals(branchName)) {
        return "test";
    } else if ("dev".equals(branchName)) {
        return "dev";
    } else {
        return "local";
    }
}

def getChangeSetDirs(changeLogSets) {

  Set output 

  for (int i = 0; i < changeLogSets.size(); i++) {
    def entries = changeLogSets[i].items
    for (int j = 0; j < entries.length; j++) {
      def entry = entries[j]
      //echo "${entry.commitId} by ${entry.author} on ${new Date(entry.timestamp)}: ${entry.msg}"
      def files = new ArrayList(entry.affectedFiles)
      for (int k = 0; k < files.size(); k++) {
            def filePath = files[k].path
            if(!filePath.startsWith("vars"))
              output.add(filePath.substring(0,filePath.firstIndexOf("/")))
      }
    }
  }

  echo "${output}"
  return output.join(",")
}
