import Foundation

/// Sends requests to the services and turns the answers into models or `APIError`s.
///
/// One client for the whole app. It carries the access token and attaches it to every call that is
/// not the login, and it reports a `401` on a signed-in call through `onUnauthorized`, so an expired
/// session sends the student back to the login from whichever screen noticed it.
@MainActor
final class APIClient {

    let environment: APIEnvironment
    private let urlSession: URLSession
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    /// The bearer token of the signed-in student, or nil.
    var accessToken: String?

    /// Called when a request that carried a token is answered with `401`.
    var onUnauthorized: (() -> Void)?

    init(environment: APIEnvironment, urlSession: URLSession = .shared) {
        self.environment = environment
        self.urlSession = urlSession
    }

    enum Method: String {
        case get = "GET", post = "POST", put = "PUT", patch = "PATCH", delete = "DELETE"
    }

    /// A request whose answer has a body.
    func request<Response: Decodable>(
        _ method: Method,
        _ service: APIService,
        _ path: String,
        query: [URLQueryItem] = [],
        body: (any Encodable)? = nil,
        authenticated: Bool = true
    ) async throws -> Response {
        let data = try await send(method, service, path, query: query, body: body, authenticated: authenticated)
        do {
            return try decoder.decode(Response.self, from: data)
        } catch {
            throw APIError.unreadableResponse
        }
    }

    /// A request whose answer is `202` or `204` with nothing worth reading.
    func requestWithoutContent(
        _ method: Method,
        _ service: APIService,
        _ path: String,
        body: (any Encodable)? = nil,
        authenticated: Bool = true
    ) async throws {
        _ = try await send(method, service, path, query: [], body: body, authenticated: authenticated)
    }

    private func send(
        _ method: Method,
        _ service: APIService,
        _ path: String,
        query: [URLQueryItem],
        body: (any Encodable)?,
        authenticated: Bool
    ) async throws -> Data {
        var components = URLComponents(
            url: environment.baseURL(for: service).appendingPathComponent(path),
            resolvingAgainstBaseURL: false
        )!
        if !query.isEmpty { components.queryItems = query }

        var request = URLRequest(url: components.url!)
        request.httpMethod = method.rawValue
        request.timeoutInterval = 15
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body {
            request.httpBody = try encoder.encode(body)
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        let sentToken = authenticated ? accessToken : nil
        if let sentToken {
            request.setValue("Bearer \(sentToken)", forHTTPHeaderField: "Authorization")
        }

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await urlSession.data(for: request)
        } catch {
            throw APIError.offline
        }

        guard let status = (response as? HTTPURLResponse)?.statusCode else {
            throw APIError.unreadableResponse
        }
        guard (200..<300).contains(status) else {
            let error = APIError.from(status: status, body: data)
            if error == .unauthorized, sentToken != nil {
                onUnauthorized?()
            }
            throw error
        }
        return data
    }
}
