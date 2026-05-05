rootProject.name = "api-users"

val emailSenderDir = file("../email-sender")
if (emailSenderDir.isDirectory) {
    includeBuild(emailSenderDir)
}
