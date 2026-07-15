import SwiftUI
import DixaMessenger

struct ContentView: View {

    @StateObject private var viewModel = ChatViewModel()

    var body: some View {
        VStack(spacing: 0) {
            connectionBanner
            messageList
            typingIndicator
            inputBar
        }
        .frame(minWidth: 360, minHeight: 480)
        .onAppear { viewModel.connect() }
    }

    private var connectionBanner: some View {
        Text("Connection: \(String(describing: viewModel.connectionState))")
            .font(.caption)
            .foregroundColor(.secondary)
            .padding(6)
    }

    private var messageList: some View {
        List(viewModel.messages, id: \.id) { message in
            VStack(alignment: .leading, spacing: 2) {
                Text(message.author == .agent ? "Agent" : "You")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text(message.text)
            }
        }
    }

    // Reserved for an "Agent is typing…" indicator.
    private var typingIndicator: some View {
        EmptyView()
    }

    private var inputBar: some View {
        HStack {
            TextField("Message…", text: $viewModel.draft)
                .textFieldStyle(.roundedBorder)
            Button("Send") { viewModel.send() }
        }
        .padding(8)
    }
}
