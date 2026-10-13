import XCTest
@testable import KApp

/// The rule behind the fixed domain in the e-mail field - the same five cases as `EmailTest.kt` on
/// Android - and how each answer of `POST /auth/login` lands on the screen.
@MainActor
final class LoginTests: XCTestCase {

    // MARK: - The address

    func testAddsTheInstitutionalDomainToWhatWasTyped() {
        XCTAssertEqual(institutionalEmail("pepito.perez"), "pepito.perez@konradlorenz.edu.co")
    }

    func testDoesNotRepeatTheDomainWhenTheWholeAddressIsPasted() {
        XCTAssertEqual(institutionalEmail("pepito.perez@konradlorenz.edu.co"), "pepito.perez@konradlorenz.edu.co")
    }

    func testReplacesAnOutsideDomainWithTheInstitutionalOne() {
        XCTAssertEqual(institutionalEmail("pepito.perez@gmail.com"), "pepito.perez@konradlorenz.edu.co")
    }

    func testIgnoresSurroundingWhitespace() {
        XCTAssertEqual(institutionalEmail("  pepito.perez  "), "pepito.perez@konradlorenz.edu.co")
    }

    func testAnEmptyFieldProducesNoAddressRatherThanABareDomain() {
        XCTAssertEqual(institutionalEmail("   "), "")
    }

    // MARK: - The answers

    private func filledModel(_ auth: FakeAuthService) -> LoginViewModel {
        let model = LoginViewModel(auth: auth)
        model.setEmail("pepito.perez@gmail.com")
        model.setPassword("ExamplePassword123")
        return model
    }

    func testAPastedAddressKeepsOnlyItsLocalPartInTheField() {
        let model = filledModel(FakeAuthService())
        XCTAssertEqual(model.emailLocalPart, "pepito.perez")
        XCTAssertEqual(model.email, "pepito.perez@konradlorenz.edu.co")
    }

    func testCannotSubmitUntilBothFieldsHaveContent() {
        let model = LoginViewModel(auth: FakeAuthService())
        XCTAssertFalse(model.canSubmit)
        model.setEmail("pepito.perez")
        XCTAssertFalse(model.canSubmit)
        model.setPassword("x")
        XCTAssertTrue(model.canSubmit)
    }

    func testASuccessfulSignInReturnsTheTokenAndSendsTheInstitutionalAddress() async {
        let auth = FakeAuthService()
        auth.loginResult = .success(FakeAuthService.token)
        let model = filledModel(auth)

        let token = await model.submit()

        XCTAssertEqual(token, FakeAuthService.token)
        XCTAssertEqual(auth.loginCalls.first?.email, "pepito.perez@konradlorenz.edu.co")
        XCTAssertEqual(model.state, .idle)
    }

    func testA401AndA400ReadTheSameSoNeitherSaysWhichFieldWasWrong() async {
        for error in [APIError.unauthorized, .badRequest("password: size must be between 10 and 72")] {
            let auth = FakeAuthService()
            auth.loginResult = .failure(error)
            let model = filledModel(auth)

            let token = await model.submit()

            XCTAssertNil(token)
            XCTAssertEqual(model.state, .invalidCredentials, "\(error)")
        }
    }

    func testA403IsTheUnverifiedAddressNotBadCredentials() async {
        let auth = FakeAuthService()
        auth.loginResult = .failure(.forbidden)
        let model = filledModel(auth)

        _ = await model.submit()

        XCTAssertEqual(model.state, .unverified(email: "pepito.perez@konradlorenz.edu.co"))
    }

    func testResendingFromTheUnverifiedStateConfirmsItWasSent() async {
        let auth = FakeAuthService()
        auth.loginResult = .failure(.forbidden)
        let model = filledModel(auth)
        _ = await model.submit()

        await model.resendVerification()

        XCTAssertEqual(model.state, .verificationSent(email: "pepito.perez@konradlorenz.edu.co"))
    }

    func testNoNetworkIsItsOwnState() async {
        let auth = FakeAuthService()
        auth.loginResult = .failure(.offline)
        let model = filledModel(auth)

        _ = await model.submit()

        XCTAssertEqual(model.state, .offline)
    }

    func testTypingAgainClearsAFailure() async {
        let auth = FakeAuthService()
        auth.loginResult = .failure(.unauthorized)
        let model = filledModel(auth)
        _ = await model.submit()

        model.setPassword("OtraContrasena123")

        XCTAssertEqual(model.state, .idle)
    }
}
