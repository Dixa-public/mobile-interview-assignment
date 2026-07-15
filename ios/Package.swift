// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "DixaMessenger",
    platforms: [
        .iOS(.v14),
        .macOS(.v11)
    ],
    products: [
        .library(name: "DixaMessenger", targets: ["DixaMessenger"])
    ],
    targets: [
        .target(name: "DixaMessenger"),
        .testTarget(
            name: "DixaMessengerTests",
            dependencies: ["DixaMessenger"]
        )
    ]
)
