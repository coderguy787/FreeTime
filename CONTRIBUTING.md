# Contributing to FreeTime

## Getting Started

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -am 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Code Style

- **Kotlin**: Follow standard Kotlin conventions with Java 17 toolchain
- **JavaScript**: 2-space indentation, single quotes, mandatory semicolons, ESLint
- **Architecture**: MVVM with Repository pattern for Android
- **Async**: Prefer async/await over callbacks

## Pull Request Guidelines

- Keep PRs focused on a single concern
- Update documentation if you change APIs or behavior
- Ensure all tests pass
- Do not include real credentials, tokens, or server URLs

## Security

- Never commit real API keys, JWT secrets, or passwords
- Use environment variables for all configuration
- Report security vulnerabilities through the process in SECURITY.md
