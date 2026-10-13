import Foundation

/// Every way a request can end other than with the answer that was asked for.
///
/// Each case is one the contracts in `docs/api/` declare, plus the two that happen before a
/// response exists: no network, and a body that does not match the schema.
enum APIError: Error, Equatable {
    /// The request never reached a server: no connection, a timeout, the mocks not running.
    case offline
    /// `400`. The server's own message, when it sent one.
    case badRequest(String?)
    /// `401`. On a signed-in call this means the token is no longer good.
    case unauthorized
    /// `403`. On the login it means the address is not verified yet; elsewhere, a role problem.
    case forbidden
    /// `404`.
    case notFound
    /// `409`. For the timetable, a class that overlaps another.
    case conflict(String?)
    /// Anything else the server answered with.
    case server(status: Int)
    /// A 2xx whose body does not decode into the contract's schema.
    case unreadableResponse

    /// Maps a non-2xx status to its case, reading `message` out of the `ApiError` body every
    /// service returns.
    static func from(status: Int, body: Data) -> APIError {
        let message = (try? JSONDecoder().decode(ServerError.self, from: body))?.message
        switch status {
        case 400: return .badRequest(message)
        case 401: return .unauthorized
        case 403: return .forbidden
        case 404: return .notFound
        case 409: return .conflict(message)
        default: return .server(status: status)
        }
    }

    /// What a screen prints when it has nothing more specific to say.
    var userMessage: String {
        switch self {
        case .offline:
            return "No pudimos conectar con el servidor. Revisa tu conexión e inténtalo otra vez."
        case .badRequest(let message):
            return message ?? "El servidor no aceptó los datos enviados."
        case .unauthorized:
            return "Tu sesión expiró. Vuelve a ingresar."
        case .forbidden:
            return "Tu cuenta no tiene acceso a esta sección."
        case .notFound:
            return "No encontramos lo que buscabas."
        case .conflict(let message):
            return message ?? "Esto choca con algo que ya existe."
        case .server:
            return "El servidor tuvo un problema. Inténtalo en un momento."
        case .unreadableResponse:
            return "El servidor respondió algo que la app no esperaba."
        }
    }

    /// The body of an error response, as `ApiError` in every contract defines it.
    private struct ServerError: Decodable {
        let message: String?
    }
}
