const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync(require('node:path').join(__dirname, '../01-Desktop-ERP/src/app.js'), 'utf8');
const start = source.indexOf('  async function submitLogin(form)');
const end = source.indexOf('\n  function showActivationError', start);
const fn = source.slice(start, end);
(async () => {
  for (const [pin, valid] of [['0123',true],['9999',true],['123',false],['123456',false],['abcd',false],['1234\n',false],['longPassword123',false],['１２３４',false]]) {
    let result;
    const context = { FormData: class { constructor(){return [['username','test@example.invalid'],['password',pin]];} },
      state:{loginCredentialMode:'password',loginPinLength:6},
      notify:(title)=>{result=title;}, localPinBudget:()=>true,
      apiConfigured:true, setApiBaseUrl:()=>true };
    await vm.runInNewContext(fn+'; submitLogin({});', context);
    assert.equal(result, valid ? 'Try again later' : 'Invalid PIN', `PIN validation: ${JSON.stringify(pin)}`);
  }
  assert(!source.slice(source.indexOf('  function loginView()'),source.indexOf('  function activationView()')).includes('6 digits'));
  console.log('8 desktop PIN regression cases passed, including stale password mode and leading zero.');
})().catch(error=>{ console.error(error);process.exitCode=1; });
