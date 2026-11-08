# JMeter DataEngine Sampler

This project runs the existing DataEngine Excel construction sequentially inside Apache JMeter. It does not convert the workbook to CSV and it does not replace the `config`, `request`, or `response` worksheets.

## Execution model

The custom sampler executes:

```text
one instance -> one environment -> selected testcase -> selected Excel row -> config steps in order
```

The JMeter GUI sampler has a **Discover / select workbook items** button. It reads the workbook with Apache POI and lets the user choose the environment, testcase indexes, and Excel rows. The same sampler can run headlessly by setting its properties in a `.jmx` file.

## Failure policy

- HTTP and GraphQL are sent through the dynamic Java HTTP client. GraphQL is emitted as HTTP POST JSON; raw `.graphql` text is wrapped as `{"query":"..."}`.
- A non-2xx HTTP status stops the entire JMeter test.
- A transport or template failure stops the entire JMeter test.
- Assertion failures are added as child results and execution continues to the next step.
- Wait values are handled inside the sequential step loop.
- A `request`-sheet `dontrun` cell is a comma- or pipe-separated list of step names for that row, for example `Auth,Upload`. Only those named steps are skipped; the row and all other steps still execute. Variables whose request header starts with a skipped step name (such as `Upload.fileId`) are not bound, and response-sheet assertions for skipped steps are not evaluated.
- `SCRIPT` and `GROOVY` steps execute a Groovy template with the request-row variables bound as Groovy variables; the returned value is exposed as `stepName.result` for later resolution.

## Supported first-pass syntax

```text
_env_
_userId_
<guid>
<guid:D>
<randomnumber:6>
<datum:yyyy-MM-dd>
<from_request_step.field_r>
<from_response_step.path_r>
```

Response-sheet assertion headers and cells support:

```text
{eq}Step.path
{contains}Step.path
{notcontains}Step.path
{startswith}Step.path
{endswith}Step.path
{notempty}Step.path
{exists}Step.path
{regex}Step.path
```

## Build

```bash
mvn test package
```

The JMeter plugin is:

```text
target/jmeter-dataengine-sampler-0.1.0-SNAPSHOT-all.jar
```

Copy the shaded JAR to JMeter's `lib/ext` directory. The JMeter runtime must also contain its normal libraries. Then restart JMeter.

## Discovery Script A

```bash
java -cp target/jmeter-dataengine-sampler-0.1.0-SNAPSHOT-all.jar \
  com.razukalias.jmeter.dataengine.DiscoveryMain \
  /path/to/Instances
```

The command prints JSON containing instance names, workbook paths, environment names, testcase names/indexes, and Excel row numbers.

## Headless sampler properties

The sampler properties are:

```text
workbook    /path/to/Instances/MyInstance/MyInstance.xlsx
instance    MyInstance
environment QA
testcases   1,3
rows       2,5,8
templates   Templates
```

The `rows` values are Excel row numbers from the `request` sheet. `*` means all rows.

This is an initial adapter implementation. The existing .NET runner remains the reference implementation for advanced features such as file streaming, C# script execution, rich history workbooks, and all token variants not listed above.

The script component is intentionally Groovy because that is the native JMeter scripting runtime. Existing `.csx` C# templates are not executed by JMeter; they must be ported to Groovy syntax.
