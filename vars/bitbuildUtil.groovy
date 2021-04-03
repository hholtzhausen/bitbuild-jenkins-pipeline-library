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
