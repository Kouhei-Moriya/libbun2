
function WhileTest() {
	var n : int = 0;
	while(n < 10) {
		assert(n < 10);
		if(n == 5) {
			continue;
		}
		n = n + 1;
	}
	println("#while/break: " + n);
	assert(n == 5);
}

function NestedWhileTest() {
	var n : int = 0;
	while(n < 10) {
		var m : int = 0;
		while(m < 10) {
			assert(m < 10);
			if(m == 5) {
				continue;
			}
			m = m + 1;
		}
		assert(m == 5);
		if(n == 5) {
			continue;
		}
		n = n + 1;
	}
	println("#while/while/break: " + n);
	assert(n == 5);
}

export function main() {
	WhileTest();
	NestedWhileTest();
}


