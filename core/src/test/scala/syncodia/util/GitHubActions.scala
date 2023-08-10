package syncodia.util

object GitHubActions:

  lazy val isGitHubAction: Boolean = sys.env.contains("GITHUB_ACTION")
