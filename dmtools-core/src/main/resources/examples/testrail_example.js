/**
 * Example JavaScript agent demonstrating TestRail MCP tools usage
 *
 * Available TestRail tools:
 * - testrail_get_projects() - Get all projects
 * - testrail_get_case(case_id) - Get test case by ID
 * - testrail_search_cases(project_name, suite_id, section_id) - Search test cases
 * - testrail_get_cases_by_refs(refs, project_name) - Find test cases by requirement reference
 * - testrail_create_case(project_name, title, description, priority_id, refs) - Create test case
 * - testrail_update_case(case_id, title, priority_id, refs) - Update test case
 * - testrail_link_to_requirement(case_id, requirement_key) - Link test case to requirement
 */
function action(params) {
    console.log('🚀 Starting TestRail JavaScript example...');

    try {
        // 1. Get list of all projects
        console.log('\n📋 Getting list of all projects...');
        const projectsJson = testrail_get_projects();
        const projects = JSON.parse(projectsJson);

        console.log(`Found ${projects.length} projects:`);
        projects.forEach(project => {
            console.log(`  - ${project.name} (ID: ${project.id})`);
        });

        // 2. Search test cases in a specific project
        if (params.projectName) {
            console.log(`\n🔍 Searching test cases in project: ${params.projectName}`);
            const casesJson = testrail_search_cases(params.projectName);
            const cases = JSON.parse(casesJson);

            console.log(`Found ${cases.length} test cases`);
            if (cases.length > 0) {
                console.log('First 3 test cases:');
                cases.slice(0, 3).forEach(testCase => {
                    console.log(`  - C${testCase.id}: ${testCase.title}`);
                });
            }
        }

        // 3. Get test case by ID
        if (params.caseId) {
            console.log(`\n📝 Getting test case C${params.caseId}...`);
            const testCaseJson = testrail_get_case(params.caseId);
            const testCase = JSON.parse(testCaseJson);

            console.log(`Title: ${testCase.title}`);
            console.log(`Priority: ${testCase.priority_id}`);
            console.log(`Created: ${new Date(testCase.created_on * 1000).toISOString()}`);
        }

        // 4. Find test cases by JIRA ticket reference
        if (params.jiraKey && params.projectName) {
            console.log(`\n🔗 Finding test cases linked to JIRA ticket: ${params.jiraKey}`);
            const linkedCasesJson = testrail_get_cases_by_refs(params.jiraKey, params.projectName);
            const linkedCases = JSON.parse(linkedCasesJson);

            console.log(`Found ${linkedCases.length} test cases linked to ${params.jiraKey}`);
            linkedCases.forEach(testCase => {
                console.log(`  - C${testCase.id}: ${testCase.title}`);
            });
        }

        // 5. Create a new test case (if createExample is true)
        if (params.createExample && params.projectName) {
            console.log(`\n✨ Creating example test case in project: ${params.projectName}`);

            const newCaseJson = testrail_create_case(
                params.projectName,
                'Example: Verify user login',
                'Steps:\n1. Navigate to login page\n2. Enter valid credentials\n3. Click Login button\n\nExpected: User is successfully logged in',
                '3', // High priority
                params.jiraKey || 'AUTO-123'
            );

            const newCase = JSON.parse(newCaseJson);
            console.log(`✅ Created test case C${newCase.id}: ${newCase.title}`);
        }

        console.log('\n✅ TestRail JavaScript example completed successfully!');

        return {
            success: true,
            projectsCount: projects.length,
            message: 'TestRail integration is working correctly'
        };

    } catch (error) {
        console.error('❌ Error in TestRail example:', error);
        return {
            success: false,
            error: error.toString()
        };
    }
}
