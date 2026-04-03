/**
 * This class is a self contained implementation of the JSON spec definined at
 * https://www.json.org/json-en.html
 * 
 * The implementation prioritises simplicity, basic json deserialisation, and
 * honestly, whatever I felt like doing
 */
public class JSON {
    private static int DEPTH_NONE = -1;
    private static String INDENT_SPACE = "  ";
    private static int MAX_OBJECT_SIZE = 256;
    private static int MAX_ARRAY_SIZE = 64;

    public static JsonValue<?> parse(String string) {
        Parser parser = new Parser(string);
        return parser.parse();
    }

    //// components
    ///
    //

    enum ValueType {
        OBJECT, ARRAY,
        NUMBER, STRING,
        BOOLEAN, NULL
    }

    // Interface to ensure all components can be pretty printed
    interface PrettyPrintable {
        String prettyString(int depth);

        public default String prettyString() {
            return prettyString(0);
        }

        default int calculateDepth(int depth) {
            if (DEPTH_NONE == depth) {
                return depth;
            }

            return depth >= 0 ? depth + 1 : depth;
        }
    }

    public static class JsonValue<T> implements PrettyPrintable {
        private ValueType _type;
        private T _underlying;

        public JsonValue(ValueType type, T underlying) {
            this._type = type;
            this._underlying = underlying;
        }

        /// constructor utility
        //

        public static JsonValue<JsonObject> newObject(JsonObject literal) {
            return new JsonValue<>(ValueType.OBJECT, literal);
        }

        public static JsonValue<JsonArray> newArray(JsonArray literal) {
            return new JsonValue<>(ValueType.ARRAY, literal);
        }

        public static JsonValue<Double> newNumber(String literal) {
            return new JsonValue<>(ValueType.NUMBER, Double.valueOf(literal));
        }

        public static JsonValue<String> newString(String literal) {
            return new JsonValue<>(ValueType.STRING, literal);
        }

        public static JsonValue<Boolean> newBoolean(String literal) {
            return new JsonValue<>(ValueType.BOOLEAN, Boolean.valueOf(literal));
        }

        public static JsonValue<Void> newNull() {
            return new JsonValue<>(ValueType.NULL, null);
        }

        /// accessors
        //

        public ValueType type() {
            return _type;
        }

        // interogations

        public boolean isObject() {
            return _type == ValueType.OBJECT;
        }

        public boolean isArray() {
            return _type == ValueType.ARRAY;
        }

        public boolean isNumber() {
            return _type == ValueType.NUMBER;
        }

        public boolean isString() {
            return _type == ValueType.STRING;
        }

        public boolean isNull() {
            return _type == ValueType.NULL;
        }

        public boolean isBoolean() {
            return _type == ValueType.BOOLEAN;
        }

        // coercions

        public JsonObject asObject() {
            return (JsonObject) _underlying;
        }

        public JsonArray asArray() {
            return (JsonArray) _underlying;
        }

        public double asNumber() {
            return (Double) _underlying;
        }

        public String asString() {
            return (String) _underlying;
        }

        public Void asNull() {
            return (Void) _underlying;
        }

        public boolean asBoolean() {
            return (Boolean) _underlying;
        }

        @Override
        public String toString() {
            return prettyString(DEPTH_NONE);
        }

        @Override
        public String prettyString(int depth) {
            return switch (_type) {
                case OBJECT -> asObject().prettyString(calculateDepth(depth));
                case ARRAY -> asArray().prettyString(calculateDepth(depth));
                case NUMBER -> String.valueOf(asNumber());
                case STRING -> asString();
                case BOOLEAN -> String.valueOf(asBoolean());
                case NULL -> String.valueOf(null);
            };
        }
    }

    public static class JsonArray implements PrettyPrintable {
        // note: this is the same idea as Scanner#value down below.
        // this could and arguably should be an array list, but i
        // prefer this approach for simplicity and lack of import
        private final JsonValue<?>[] _values;

        public JsonArray(JsonValue<?>[] values) {
            this._values = values;
        }

        public JsonValue<?> get(int index) {
            return _values[index];
        }

        @Override
        public String toString() {
            return prettyString(DEPTH_NONE);
        }

        @Override
        public String prettyString(int depth) {
            StringBuilder builder = new StringBuilder("[ ");

            for (int i = 0; i < _values.length; i++) {
                JsonValue<?> value = _values[i];

                if (value != null) {
                    // indent
                    builder.repeat('\n', depth > 0 ? 1 : 0);
                    builder.repeat(INDENT_SPACE, depth <= 0 ? 0 : depth);

                    // data
                    builder.append(value.prettyString(depth));
                    builder.append(", ");
                }
            }

            if (depth >= 0) {
                builder.repeat('\n', depth > 0 ? 1 : 0);
            }

            int lastComma = builder.lastIndexOf(",");
            builder.replace(lastComma, lastComma + 1, "");

            if (depth > 1) {
                builder.repeat(INDENT_SPACE, depth - 1);
            }

            builder.append("]");
            return builder.toString();
        }
    }

    public static class JsonObject implements PrettyPrintable {
        private final int _size = MAX_OBJECT_SIZE;
        private final JsonNode[] _nodes = new JsonNode[_size];
        private final int[] _ordinals = new int[_size];

        private int _count;

        public JsonObject() {
            this._count = 0;
        }

        public boolean put(JsonNode node) {
            if (_count >= _size) {
                return false;
            }

            int start = (_size - 1) & hash(node.key());
            int insert = start;

            while (true) {
                if (_nodes[insert] == null) {
                    _nodes[insert] = node;
                    _ordinals[_count] = insert;

                    _count += 1;
                    return true;
                }

                insert += 1;

                if (insert >= _size) {
                    insert = 0;
                } else if (insert == start) {
                    return false;
                }
            }
        }

        public JsonNode get(String key) {
            int start = (_size - 1) & hash(key);
            int index = start;

            while (true) {
                if (_nodes[index] == null) {
                    return null;
                }

                JsonNode node = _nodes[index];

                if (key.equals(node.key())) {
                    return node;
                }

                index += 1;

                if (index >= _size) {
                    index = 0;
                } else if (index == start) {
                    return null;
                }
            }
        }

        // note: shamelessly liberated from HashMap
        private int hash(Object key) {
            int h;
            return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
        }

        @Override
        public String toString() {
            return prettyString(DEPTH_NONE);
        }

        @Override
        public String prettyString(int depth) {
            StringBuilder builder = new StringBuilder("{ ");

            for (int i = 0; i < _count; i++) {
                int index = _ordinals[i];
                JsonNode node = _nodes[index];

                if (node != null) {
                    // indent
                    builder.repeat('\n', depth > 0 ? 1 : 0);

                    // data
                    builder.append(node.prettyString(depth));
                    builder.append(", ");
                }
            }

            if (depth >= 0) {
                builder.repeat('\n', depth > 0 ? 1 : 0);
            }

            int lastComma = builder.lastIndexOf(",");
            builder.replace(lastComma, lastComma + 1, "");

            if (depth > 1) {
                builder.repeat(INDENT_SPACE, depth - 1);
            }

            builder.append("}");

            return builder.toString();
        }
    }

    public static class JsonNode implements PrettyPrintable {
        protected String _key;
        protected JsonValue<?> _value;

        public <T> JsonNode(String key, JsonValue<T> value) {
            this._key = key;
            this._value = value;
        }

        public String key() {
            return _key;
        }

        public JsonValue<?> value() {
            return _value;
        }

        public ValueType type() {
            return _value._type;
        }

        public boolean isEmpty() {
            return _key.isEmpty() && _value._type == ValueType.NULL;
        }

        @Override
        public String toString() {
            return prettyString(DEPTH_NONE);
        }

        @Override
        public String prettyString(int depth) {
            return "%s%s: %s".formatted(INDENT_SPACE.repeat(depth <= 0 ? 0 : depth), _key, _value.prettyString(depth));
        }
    }

    //// internals
    ///
    //

    /// parser
    //

    public static class Parser {
        /// data
        //
        private final Scanner _scanner;

        /// state
        //
        private int _depth = -1;
        private Token _token = null;

        public Parser(String source) {
            this._scanner = new Scanner(source);
        }

        /**
         * Entry point, parses source string to json elements
         * 
         * @return A json tree encapsulated by {@link JsonValue}
         */
        public JsonValue<?> parse() {
            unsafeScanToken();
            return value();
        }

        /// json components
        //

        /**
         * Json value parsing logic
         * 
         * @return A {@link JsonValue}
         */
        private JsonValue<?> value() {
            JsonValue<?> value = null;

            switch (_token.type) {
                case OPEN_OBJECT: {
                    unsafeScanToken();
                    value = JsonValue.newObject(object());
                    break;
                }
                case OPEN_ARRAY: {
                    unsafeScanToken();
                    value = JsonValue.newArray(array());
                    break;
                }
                case STRING_LITERAL: {
                    value = JsonValue.newString(_token.literal);
                    break;
                }
                case NUMBER_LITERAL: {
                    value = JsonValue.newNumber(_token.literal);
                    break;
                }
                case TRUE, FALSE: {
                    value = JsonValue.newBoolean(_token.literal);
                    break;
                }
                case NULL: {
                    value = JsonValue.newNull();
                    break;
                }
                case EOF: {
                    if (top()) {
                        value = JsonValue.newNull();
                        break;
                    }
                    // let this fall through to default, i.e, produce an error
                }
                default: {
                    error("Unexpected token");
                }
            }

            unsafeScanToken();
            return value;
        }

        /**
         * Json object parsing logic
         * 
         * @return A {@link JsonObject}
         */
        private JsonObject object() {
            JsonObject object = new JsonObject();

            _depth += 1;

            while (_token.type != TokenType.CLOSE_OBJECT && !end()) {
                if (!match(TokenType.STRING_LITERAL)) {
                    error("Expected key in object");
                }

                String key = _token.literal;
                expect(TokenType.COLON);

                JsonValue<?> value = value();
                object.put(new JsonNode(key, value));

                if (match(TokenType.COMMA)) {
                    unsafeScanToken();
                }
            }

            if (_token.type != TokenType.CLOSE_OBJECT) {
                error("Expected end of object");
            }

            _depth -= 1;

            return object;
        }

        /**
         * Json array parsing logic
         * 
         * @return A {@link JsonArray}
         */
        private JsonArray array() {
            JsonValue<?>[] values = new JsonValue[MAX_ARRAY_SIZE];
            int index = 0;

            _depth += 1;

            while (_token.type != TokenType.CLOSE_ARRAY && !end()) {
                values[index] = value();
                index += 1;

                if (match(TokenType.COMMA)) {
                    unsafeScanToken();
                }
            }

            if (_token.type != TokenType.CLOSE_ARRAY) {
                error("Expected end of array");
            }

            _depth -= 1;

            return new JsonArray(values);
        }

        /// auxiliaries
        //

        /**
         * Creates a runtime expection with message
         * 
         * @param message The first part of error message
         */
        private void error(String message) {
            String errorMessage = "%s found '%s', at %d:%d-%d"
                    .formatted(message, _token.type.name(), _token.line, _token.start, _token.end);
            throw new RuntimeException(errorMessage);
        }

        /**
         * Checks the current depth state of parser, specifically,
         * whether the depth is top level
         *
         * @return Whether parser is currently at top level depth
         */
        private boolean top() {
            return _depth == -1;
        }

        /**
         * Checks the scanner position in source string,
         * returning a boolean whether it is at the end of source.
         *
         * @return Whether scanner is at the end of source
         */
        private boolean end() {
            return _scanner.end();
        }

        /**
         * Checks whether the next parser token matches an expected type.
         * An invalid match is considered invalid state, and an error is produced
         * 
         * @param type The token type to test for
         * @throws JsonParserException if match fails
         */
        private void expect(TokenType type) {
            unsafeScanToken();

            if (_token.type != type) {
                error("Cannot parse '" + _token.literal + "', expected token type '%s'");
            }

            unsafeScanToken();
        }

        /**
         * Accepts multiple token types to match against the current parser token.
         * The logic checks for any matches, where the first match returns true early,
         * otherwise, false
         * 
         * @param tests One or more token types to test for
         * @return True if any type matches, false otherwise
         */
        private boolean match(TokenType... tests) {
            for (TokenType type : tests) {
                if (_token.type == type) {
                    return true;
                }
            }

            return false;
        }

        /**
         * Calls scanner for next token, updating local current token for parser
         * instance if an error token is encountered a runtime exception is produced
         * instead
         * 
         * @throws JsonParserException if error is encountered
         */
        private void unsafeScanToken() {
            Token ambiguous = _scanner.scanToken();

            // note: in theory, there should only ever be one whitespace token
            // produced for a sequence of whitespace characters.
            // looping is redundant here, otherwise, there is a likely bug with
            // the scanner logic
            while (ambiguous.type() == TokenType.WHITESPACE) {
                ambiguous = _scanner.scanToken();
            }

            if (ambiguous.type() == TokenType.ERR) {
                throw new RuntimeException(ambiguous.literal());
            }

            _token = ambiguous;
        }
    }

    /// tokens
    //

    // All json tokens produced by scanner
    enum TokenType {
        OPEN_OBJECT, CLOSE_OBJECT,
        OPEN_ARRAY, CLOSE_ARRAY,
        STRING_LITERAL, NUMBER_LITERAL,
        COLON, COMMA,
        TRUE, FALSE,
        WHITESPACE,
        NULL,
        EOF,
        // note: initally, i had another `TokenError` pojo to encapsulate error tokens,
        // but that seemed overkill. this approach is simple and alignes with the
        // philosophy of this project, although i might change my mind in the future
        ERR,
    }

    // Read-only pojo encapsulating TokenType and meta data
    record Token(
            TokenType type,
            int start,
            int end,
            int line,
            String literal) {
    }

    /// scanner
    //

    public static class Scanner {
        /// data
        //
        private final String _source; // source string

        /// state
        //
        private int _line = 1; // current line
        private int _mark = 0; // last point marked in string
        private int _cursor = 0; // current cursor position in string

        public Scanner(String source) {
            this._source = source;
        }

        /**
         * Entry point of Scanner. Triggers the scanner to process another token.
         *
         * @return A Token representing the processed lexeme
         */
        public Token scanToken() {
            mark();
            return end() ? makeToken(TokenType.EOF) : processToken(advance());
        }

        /**
         * Core scanner logic. Accepts the current character being scanned and emits
         * token for corresponding lexeme.
         * This method advances the cursor position after the processed lexeme.
         *
         * @param c The current character to scan
         * @return A Token representing the processed lexeme
         */
        private Token processToken(char c) {
            return switch (c) {
                // whitespace
                case ' ', '\r', '\t' -> whitespace();
                case '\n' -> {
                    _line++;

                    yield whitespace();
                }

                // structural
                case '{' ->

                    makeToken(TokenType.OPEN_OBJECT);
                case '}' -> makeToken(TokenType.CLOSE_OBJECT);
                case '[' -> makeToken(TokenType.OPEN_ARRAY);
                case ']' -> makeToken(TokenType.CLOSE_ARRAY);
                case ',' -> makeToken(TokenType.COMMA);
                case ':' -> makeToken(TokenType.COLON);

                // literal
                case '"' -> string();
                case '-' -> {
                    if (validDigit(peek())) {

                        yield number();

                    } else

                    {

                        yield error("Expected number after '-'");
                    }

                }
                default -> {
                    if (validDigit(c)) {

                        yield number();

                    } else if (validAlpha(c)) {

                        yield value();

                    } else {

                        yield error("Unexpected token");
                    }

                }

            };
        }

        /// literals
        //

        /**
         * Constructs {@link TokenType#WHITESPACE} tokens
         *
         * @return A {@link TokenType#WHITESPACE} token
         */
        private Token whitespace() {
            Token token;

            loop: while (true) {
                char c = peek();
                switch (c) {
                    case ' ', '\r', '\t' -> advance();
                    case '\n' -> {
                        _line++;
                        advance();
                    }
                    default -> {
                        token = makeToken(TokenType.WHITESPACE);
                        break loop;
                    }
                }
            }

            return token;
        }

        /**
         * Constructs {@link TokenType#NUMBER_LITERAL} tokens
         *
         * @return A {@link TokenType#NUMBER_LITERAL} token
         */
        private Token number() {
            advance();
            // handle basic number
            digits();

            // handle fraction
            if (match('.')) {
                digits();
            }

            // handle exponent
            if (match('e') || match('E')) {
                if (match('+') || match('-')) {
                    digits();
                } else {
                    return error("Error parsing number");
                }
            }

            return makeToken(TokenType.NUMBER_LITERAL);
        }

        /**
         * Constructs {@link TokenType#STRING_LITERAL} tokens
         *
         * @return A {@link TokenType#STRING_LITERAL} token
         */
        private Token string() {
            // note: according to the json spec, this implementation
            // is crude. i believe this can parse technically invalid
            // json strings, but it should be fine for now
            while (peek() != '"' && !end()) {
                if (peek() == '\n') {
                    _line++;
                }
                advance();
            }

            if (end()) {
                return error("Error processing string");
            }

            advance();
            return makeToken(TokenType.STRING_LITERAL);
        }

        /**
         * Constructs keyword word value tokens, i.e, {@link TokenType#TRUE},
         * {@link TokenType#FALSE}, and {@link TokenType#NULL}
         *
         * @return A value token
         */
        private Token value() {
            while (validAlpha(peek()) && !end()) {
                // note: because the number of keywords is so small
                // we can return early here since we know any value
                // longer than the longest value, false, is invalid
                if (_cursor - _mark > 5) {
                    return error("Error processing value 1");
                }
                advance();
            }

            String str = substring();
            TokenType type = null;

            // note: originally, these were contained in a map of keywords.
            // i prefer this approach since the number of keywords, and
            // it lets me remove the dependency on java.util.HashMap
            if ("true".equals(str)) {
                type = TokenType.TRUE;
            } else if ("false".equals(str)) {
                type = TokenType.FALSE;
            } else if ("null".equals(str)) {
                type = TokenType.NULL;
            }

            if (type == null) {
                return error("Error processing value 2");
            }

            return makeToken(type);
        }

        /// auxiliaries
        //

        /**
         * Validates if character is a lower alphabetic letter, used for value
         * processing
         *
         * @param c The character to test
         * @return True if character is alphabetic, false otherwise
         */
        private boolean validAlpha(char c) {
            return (c >= 'a' && c <= 'z');
        }

        /**
         * Validates if character is a number from 0 to 9
         *
         * @param c The character to test
         * @return True if character is a digit, false otherwise
         */
        private boolean validDigit(char c) {
            return c >= '0' && c <= '9';
        }

        /**
         * Substrings the current lexeme, using last mark and current cursor position
         *
         * @return A string from mark to cursor position
         */
        private String substring() {
            return _source.substring(_mark, _cursor);
        }

        /**
         * Consumes any digits, returning when a non-digit character is enountered
         * 
         * This method is a anciliary to {@link Scanner#number}
         */
        private void digits() {
            while (validDigit(peek())) {
                advance();
            }
        }

        // token production

        /**
         * Creates a {@link Token} with type {@link TokenType#ERR} where literal is the
         * specified error message.
         *
         * @param message The message for the error encountered
         * @return An error {@link Token} for the scanner position
         */
        private Token error(String message) {
            return new Token(TokenType.ERR, _mark, _cursor, _line, message);
        }

        /**
         * Creates a {@link Token} for a given {@link TokenType}.
         *
         * @param type The STATE of {@link Token} to be created
         * @return {@link Token} with specified {@link TokenType}
         */
        private Token makeToken(TokenType type) {
            String literal = substring();
            return new Token(type, _mark, _cursor, _line, literal);
        }

        // state progression

        /**
         * Returns a boolean whether the provided char matches the char at the current
         * cursor position.
         * This operation increments the cursor if there is a match.
         *
         * @param test Character to test for
         * @return Whether given character matches current cursor position
         */
        private boolean match(char test) {
            if (end() || test != _source.charAt(_cursor))
                return false;

            _cursor++;
            return true;
        }

        /**
         * Returns char at current cursor position.
         * This is a non-incrementing operation.
         *
         * @return Character at cursor position at time of invocation
         */
        private char peek() {
            if (end())
                return '\0';
            return _source.charAt(_cursor);
        }

        /**
         * Updates marker position to current cursor position
         */
        private void mark() {
            _mark = _cursor;
        }

        /**
         * Returns char at current cursor position.
         * This increments the cursor position by one.
         *
         * @return Character at cursor position at time of invocation
         */
        private char advance() {
            return _source.charAt(_cursor++);
        }

        /**
         * Checks the cursors position in source string,
         * returning a boolean whether it is at the end of source.
         *
         * @return Whether cursor is at the end of source
         */
        private boolean end() {
            return _cursor >= _source.length();
        }
    }
}
