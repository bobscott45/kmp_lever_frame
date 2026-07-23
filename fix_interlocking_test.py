import re

filepath = 'shared/src/commonTest/kotlin/org/edranor/leverframe/InterlockingTest.kt'
with open(filepath, 'r') as f:
    content = f.read()

# Fix testSimpleLock
content = content.replace(
'''        val leversMod = createLevers(true, false)

        assertFalse(Interlocking.evaluate(tab, levers, emptyList(), 1, true))

        assertTrue(Interlocking.evaluate(tab, levers, emptyList(), 0, false))''',
'''        val leversMod = createLevers(true, false)

        assertFalse(Interlocking.evaluate(tab, leversMod, emptyList(), 1, true))

        assertTrue(Interlocking.evaluate(tab, leversMod, emptyList(), 0, false))'''
)

# Fix testMutualLocking
content = content.replace(
'''        val leversMod = createLevers(true, false)
        
        assertFalse(Interlocking.evaluate(tab, levers, emptyList(), 1, true))''',
'''        val leversMod = createLevers(true, false)
        
        assertFalse(Interlocking.evaluate(tab, leversMod, emptyList(), 1, true))'''
)

with open(filepath, 'w') as f:
    f.write(content)
