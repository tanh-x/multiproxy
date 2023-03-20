import proxy.ProxyHelpers
import proxy.ProxyHelpers.buildURLRequest
import proxy.ProxyMonitor
import java.io.File

fun main(args: Array<String>) {
    val pm: ProxyMonitor = ProxyMonitor(File("proxies.txt"))
    val s = pm.handle(buildURLRequest("https://friends.roblox.com/v1/users/106347682/friends"))
    pm.handle(buildURLRequest("https://friends.roblox.com/v1/users/106347682/friends"))
    pm.handle(buildURLRequest("https://friends.roblox.com/v1/users/106347682/friends"))
    pm.handle(buildURLRequest("https://friends.roblox.com/v1/users/106347682/friends"))
    pm.handle(buildURLRequest("https://friends.roblox.com/v1/users/106347682/friends"))
    pm.handle(buildURLRequest("https://friends.roblox.com/v1/users/106347682/friends"))
    pm.handle(buildURLRequest("https://friends.roblox.com/v1/users/106347682/friends"))
    pm.handle(buildURLRequest("https://friends.roblox.com/v1/users/106347682/friends"))
    pm.handle(buildURLRequest("https://friends.roblox.com/v1/users/106347682/friends"))
    pm.handle(buildURLRequest("https://friends.roblox.com/v1/users/106347682/friends"))
    pm.handle(buildURLRequest("https://friends.roblox.com/v1/users/106347682/friends"))
    println(s)
}