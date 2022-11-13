package mars.tools

import mars.Globals
import mars.detectRadix
import mars.mips.hardware.*
import mars.toHex
import mars.util.Binary
import java.awt.*
import java.awt.BorderLayout.*
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.util.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

/*
Copyright (c) 2010-2011,  Pete Sanderson and Kenneth Vollmar

Developed by Pete Sanderson (psanderson@otterbein.edu)
and Kenneth Vollmar (kenvollmar@missouristate.edu)

Permission is hereby granted, free of charge, to any person obtaining 
a copy of this software and associated documentation files (the 
"Software"), to deal in the Software without restriction, including 
without limitation the rights to use, copy, modify, merge, publish, 
distribute, sublicense, and/or sell copies of the Software, and to 
permit persons to whom the Software is furnished to do so, subject 
to the following conditions:

The above copyright notice and this permission notice shall be 
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, 
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF 
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. 
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR 
ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF 
CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION 
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

(MIT license, http://www.opensource.org/licenses/mit-license.html)
 */
/**
 * Bitmapp display simulator.  It can be run either as a stand-alone Java application having access to the mars package,
 * or through MARS as an item in its Tools menu.  It makes maximum use of methods inherited from its abstract superclass
 * AbstractMarsToolAndApplication. Pete Sanderson, verison 1.0, 23 December 2010.
 */
class BitmapDisplay : AbstractMarsToolAndApplication
{
    // Major GUI components
    private lateinit var uiBaseAddressSelector: JComboBox<String>
    private lateinit var uiKeyboardCheckbox: JCheckBox
    private lateinit var canvas: JPanel
    private lateinit var results: JPanel

    // Some GUI settings
    private val emptyBorder = EmptyBorder(4, 4, 4, 4)
    private val backgroundColor = Color.WHITE

    // Values for display canvas.  Note their initialization uses the identifiers just above.
    private var unitWidth: Int = 2
    private var unitHeight: Int = 2
    private var displayWidth: Int = 512
    private var displayHeight: Int = 256
    private val displayDimension get() = Dimension(displayWidth, displayHeight)

    private val baseAddresses = intArrayOf(Memory.dataSegmentBaseAddress, Memory.globalPointer, Memory.dataBaseAddress,
        Memory.heapBaseAddress, Memory.memoryMapBaseAddress)

    private val baseAddress get() = baseAddresses[uiBaseAddressSelector.selectedIndex]

    // Keyboard++
    private var keyboardAddr: UInt = 0xFFFF0010u
    private var oldKeyboardAddr: UInt = 0xFFFF0000u
    private var oldKeyboardLastPressed: Char = ' '
    private var keyboardAttached = false
    private lateinit var uiKeyboard: JTextField

    private lateinit var grid: Grid

    val pooledKeyEvents = hashMapOf<UInt, ArrayList<KeyEvent>>(
        0x00u to ArrayList(), 0x10u to ArrayList(), 0x20u to ArrayList()
    )
    val downKeys = HashSet<Int>()

    /**
     * Simple constructor, likely used to run a stand-alone bitmap display tool.
     *
     * @param title String containing title for title bar
     * @param heading String containing text for heading shown in upper part of window.
     */
    constructor(title: String, heading: String) : super(title, heading)

    constructor() : super("Azalea's Bitmap Display++", HEADING)

    override fun getName() = HEADING

    /**
     * Override the inherited method, which registers us as an Observer over the static data segment (starting address
     * 0x10010000) only.  This version will register us as observer over the the memory range as selected by the base
     * address combo box and capacity of the visualization display (number of visualization elements times the number of
     * memory words each one represents). It does so by calling the inherited 2-parameter overload of this method. If
     * you use the inherited GUI buttons, this method is invoked when you click "Connect" button on MarsTool or the
     * "Assemble and Run" button on a Mars-based app.
     */
    override fun addAsObserver()
    {
        var highAddress: Int = baseAddress + grid.rows * grid.cols * Memory.WORD_LENGTH_BYTES
        // Special case: baseAddress<0 means we're in kernel memory (0x80000000 and up) and most likely
        // in memory map address space (0xffff0000 and up).  In this case, we need to make sure the high address
        // does not drop off the high end of 32 bit address space.  Highest allowable word address is 0xfffffffc,
        // which is interpreted in Java int as -4.
        if (baseAddress < 0 && highAddress > -4)
        {
            highAddress = -4
        }
        addAsObserver(baseAddress, highAddress)
        addAsObserver(keyboardAddr.toInt(), (keyboardAddr + 0x20u).toInt())
    }

    /**
     * Method that constructs the main display area.  It is organized vertically into two major components: the display
     * configuration which an be modified using combo boxes, and the visualization display which is updated as the
     * attached MIPS program executes.
     *
     * @return the GUI component containing these two areas
     */
    override fun buildMainDisplayArea(): JComponent
    {
        results = JPanel()
        results.add(buildOrganizationArea())
        results.add(buildVisualizationArea())
        return results
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //  Rest of the protected methods.  These override do-nothing methods inherited from
    //  the abstract superclass.
    //////////////////////////////////////////////////////////////////////////////////////
    /**
     * Update display when connected MIPS program accesses (data) memory.
     *
     * @param memory the attached memory
     * @param ac information provided by memory in MemoryAccessNotice object
     */
    override fun processMIPSUpdate(memory: Observable, ac: AccessNotice)
    {
        if (ac !is MemoryAccessNotice || ac.accessType != AccessNotice.WRITE) return
        val addr = ac.address.toUInt()

        // For the keyboard++
        if (addr in keyboardAddr..keyboardAddr + 0x30u)
        {
            // Offset 0x01 or 0x11 or 0x21 bytes are for telling the keyboard that the events are received
            var offset = addr - keyboardAddr

            // Clear the segment
            if (offset == 0x01u || offset == 0x11u)
            {
                if (Globals.memory.getByte(addr.toInt()) != 1) return

                println("Offset written: ${offset.toHex(2)}")

                // Clear bytes if 1 is written to the 0x01 offset
                offset -= 1u
                val range = offset..offset + 0x0Fu
                synchronized(Globals.memoryAndRegistersLock)
                {
                    range.map { keyboardAddr + it }.forEach { Globals.memory.setByte(it.toInt(), 0) }
                }
                pooledKeyEvents[offset]!!.clear()
                println("[Keyboard++] Keyboard segment range $range cleared")
            }
            return
        }

        // For the display
        val value = ac.value
        val offset = ((addr - baseAddress.toUInt()) / 4u).toInt()
        try
        {
            grid.setElement(offset / grid.cols, offset % grid.cols, value)
        }
        catch (e: IndexOutOfBoundsException)
        {
            // If address is out of range for display, do nothing.
        }
        if (offset == 0) canvas.repaint()
    }

    /**
     * Event on key press
     *
     * @param offset Event type / offset
     * @param e Event
     */
    fun keyEvent(offset: UInt, e: KeyEvent)
    {
        if (!keyboardAttached) return
        println("[Keyboard++] ${e.id} '${e.keyChar}' ${e.keyCode}")
        val queue = pooledKeyEvents[offset]!!

        // Old keyboard compatibility
        synchronized(Globals.memoryAndRegistersLock)
        {
            if (e.id == KeyEvent.KEY_PRESSED)
            {
                oldKeyboardLastPressed = e.keyChar
                Globals.memory.setWord(oldKeyboardAddr.toInt(), 1)
                Globals.memory.setWord((oldKeyboardAddr + 4u).toInt(), e.keyChar.code)
            }

            if (e.id == KeyEvent.KEY_RELEASED && e.keyChar == oldKeyboardLastPressed)
            {
                oldKeyboardLastPressed = 0.toChar()
                Globals.memory.setWord(oldKeyboardAddr.toInt(), 0)
                Globals.memory.setWord((oldKeyboardAddr + 4u).toInt(), 0)
            }
        }

        // Check for more than 7 key events queued
        if (queue.size == 7) return

        // Add to queue
        queue.add(e)

        // Add to memory
        var addr = keyboardAddr + offset
        println("[Keyboard++] Address ${addr.toHex(8)} set to ${queue.size} | ${addr.toHex(8)} set to ${e.keyCode} (${e.keyChar.toHex()})")
        synchronized(Globals.memoryAndRegistersLock)
        {
            // Change 0x0: Number of events
            Globals.memory.setByte(addr.toInt(), queue.size)

            // Set the keycode
            addr += queue.size.toUInt() * 2u
            Globals.memory.setHalf(addr.toInt(), e.keyCode)
        }
    }

    /**
     * Initialize all JComboBox choice structures not already initialized at declaration. Overrides inherited method
     * that does nothing.
     */
    override fun initializePreGUI()
    {
        grid = Grid(0, 0)
    }

    /**
     * The only post-GUI initialization is to create the initial Grid object based on the default settings of the
     * various combo boxes. Overrides inherited method that does nothing.
     */
    override fun initializePostGUI()
    {
        grid = createNewGrid()
    }

    /**
     * Method to reset counters and display when the Reset button selected. Overrides inherited method that does
     * nothing.
     */
    override fun reset()
    {
        grid.reset()
        canvas.repaint()
    }

    /**
     * Overrides default method, to provide a Help button for this tool/app.
     */
    override fun getHelpComponent(): JComponent
    {
        val helpContent = """
             Use this program to simulate a basic bitmap display where
             each memory word in a specified address space corresponds to
             one display pixel in row-major order starting at the upper left
             corner of the display.  This tool may be run either from the
             MARS Tools menu or as a stand-alone application.
             
             You can easily learn to use this small program by playing with
             it!   Each rectangular unit on the display represents one memory
             word in a contiguous address space starting with the specified
             base address.  The value stored in that word will be interpreted
             as a 24-bit RGB color value with the red component in bits 16-23,
             the green component in bits 8-15, and the blue component in bits 0-7.
             Each time a memory word within the display address space is written
             by the MIPS program, its position in the display will be rendered
             in the color that its value represents.
             
             Version 1.0 is very basic and was constructed from the Memory
             Reference Visualization tool's code.  Feel free to improve it and
             send me your code for consideration in the next MARS release.
             
             Contact Pete Sanderson at psanderson@otterbein.edu with
             questions or comments.
             
             """.trimIndent()
        val help = JButton("Help")
        help.addActionListener { JOptionPane.showMessageDialog(theWindow, helpContent) }
        return help
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //  Private methods defined to support the above.
    //////////////////////////////////////////////////////////////////////////////////////
    // UI components and layout for left half of GUI, where settings are specified.
    private fun buildOrganizationArea(): JComponent
    {
        val uiUnitWidthSelector = JComboBox(arrayOf(1, 2, 4, 8, 16, 32)).apply {
            isEditable = false
            background = backgroundColor
            selectedIndex = 1
            toolTipText = "Width in pixels of rectangle representing memory word"
            addActionListener {
                unitWidth = getInt()
                grid = createNewGrid()
                reset()
            }
        }

        val uiUnitHeightSelector = JComboBox(arrayOf(1, 2, 4, 8, 16, 32)).apply {
            isEditable = false
            background = backgroundColor
            selectedIndex = 1
            toolTipText = "Height in pixels of rectangle representing memory word"
            addActionListener {
                unitHeight = getInt()
                grid = createNewGrid()
                reset()
            }
        }

        val uiWidthSelector = JComboBox(arrayOf(64, 128, 256, 512, 1024)).apply {
            isEditable = false
            background = backgroundColor
            selectedIndex = 3
            toolTipText = "Total width in pixels of display area"
            addActionListener {
                displayWidth = getInt()
                canvas.preferredSize = displayDimension
                canvas.size = displayDimension
                grid = createNewGrid()
                reset()
            }
        }

        val uiHeightSelector = JComboBox(arrayOf(64, 128, 256, 512, 1024)).apply {
            isEditable = false
            background = backgroundColor
            selectedIndex = 2
            toolTipText = "Total height in pixels of display area"
            addActionListener {
                displayHeight = getInt()
                canvas.preferredSize = displayDimension
                canvas.size = displayDimension
                grid = createNewGrid()
                reset()
            }
        }

        val descriptions = arrayOf("global data", "\$gp", "static data", "heap", "memory map")

        val displayBaseAddressChoices = baseAddresses
            .mapIndexed { i, it -> "${Binary.intToHexString(it)} (${descriptions[i]})" }.toTypedArray()

        uiBaseAddressSelector = JComboBox(displayBaseAddressChoices).apply {
            isEditable = false
            background = backgroundColor
            selectedIndex = 1
            toolTipText = "Base address for display area (upper left corner)"
            addActionListener {
                // If display base address is changed while connected to MIPS (this can only occur
                // when being used as a MarsTool), we have to delete ourselves as an observer and re-register.
                if (connectButton != null && connectButton.isConnected)
                {
                    deleteAsObserver()
                    addAsObserver()
                }
                grid = createNewGrid()
                reset()
            }
        }

        val uiKeyboardAddress = JTextField(keyboardAddr.toHex(8)).apply {
            addActionListener {
                text.toUIntOrNull(text.detectRadix())?.let {
                    keyboardAddr = it
                    uiKeyboardCheckbox.isSelected = false
                }
            }
        }
        val uiKeyboardCheckbox = JCheckBox("Attach Keyboard++", keyboardAttached).apply {
            addActionListener {
                keyboardAttached = isSelected
                if (isSelected)
                {
                    deleteAsObserver()
                    addAsObserver()
                }
                pooledKeyEvents.values.forEach { it.clear() }
            }
        }

        println("Bitmap Display++ Initialized")

        // Register key listener
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher {
            if (!(keyboardAttached && uiKeyboard.hasFocus())) return@addKeyEventDispatcher false
            when (it.id)
            {
                KeyEvent.KEY_PRESSED ->
                {
                    // Keep track of down keys to avoid the system's key repeat
                    if (!downKeys.contains(it.keyCode))
                    {
                        keyEvent(0x00u, it)
                        downKeys.add(it.keyCode)
                    }
                }
                KeyEvent.KEY_RELEASED ->
                {
                    keyEvent(0x10u, it)
                    downKeys.remove(it.keyCode)
                }
            }
            true
        }
        uiKeyboard = JTextField(2).apply {
            class KL : KeyListener
            {
                override fun keyTyped(e: KeyEvent) {
                    uiKeyboard.text = ""
                }
                override fun keyPressed(e: KeyEvent) {
                    uiKeyboard.text = ""
                    // keyEvent(0x0u, e)
                }
                override fun keyReleased(e: KeyEvent) {
                    uiKeyboard.text = ""
                    // keyEvent(0x10u, e)
                }
            }

            addKeyListener(KL())
        }

        // Lay 'em out in the grid...
        return JPanel(GridLayout(8, 1)).apply {
            add(getPanelWithBorderLayout().apply {
                add(JLabel("Unit Width in Pixels "), WEST)
                add(uiUnitWidthSelector, EAST)
            })
            add(getPanelWithBorderLayout().apply {
                add(JLabel("Unit Height in Pixels "), WEST)
                add(uiUnitHeightSelector, EAST)
            })
            add(getPanelWithBorderLayout().apply {
                add(JLabel("Display Width in Pixels "), WEST)
                add(uiWidthSelector, EAST)
            })
            add(getPanelWithBorderLayout().apply {
                add(JLabel("Display Height in Pixels "), WEST)
                add(uiHeightSelector, EAST)
            })
            add(getPanelWithBorderLayout().apply {
                add(JLabel("Base address for display "), WEST)
                add(uiBaseAddressSelector, EAST)
            })
            add(getPanelWithBorderLayout().apply {
                add(uiKeyboardCheckbox, WEST)
                add(uiKeyboardAddress, EAST)
            })
            add(getPanelWithBorderLayout().apply {
                add(JLabel("To use keyboard, put your cursor here >"), WEST)
                add(uiKeyboard)
            })
        }
    }

    // UI components and layout for right half of GUI, the visualization display area.
    private fun buildVisualizationArea(): JComponent
    {
        canvas = GraphicsPanel()
        canvas.preferredSize = displayDimension
        canvas.toolTipText = "Bitmap display area"
        return canvas
    }

    private fun <T> JComboBox<T>.getInt() = selectedItem!!.toString().toInt()

    // Use this for consistent results.
    private fun getPanelWithBorderLayout(): JPanel = JPanel(BorderLayout(2, 2)).apply { border = emptyBorder }

    // Method to determine grid dimensions based on current control settings.
    // Each grid element corresponds to one visualization unit.
    private fun createNewGrid(): Grid
    {
        val rows = displayHeight / unitHeight
        val columns = displayWidth / unitWidth
        return Grid(rows, columns)
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //  Specialized inner classes for modeling and animation.
    //////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////
    //  Class that represents the panel for visualizing and animating memory reference
    //  patterns.
    private inner class GraphicsPanel : JPanel()
    {
        // override default paint method to assure display updated correctly every time
        // the panel is repainted.
        override fun paint(g: Graphics) = paintGrid(g, grid)

        // Paint the color codes.
        private fun paintGrid(g: Graphics, grid: Grid)
        {
            var upperLeftX = 0
            var upperLeftY = 0
            for (i in 0 until grid.rows)
            {
                for (j in 0 until grid.cols)
                {
                    g.color = grid.getElementFast(i, j)
                    g.fillRect(upperLeftX, upperLeftY, unitWidth, unitHeight)
                    upperLeftX += unitWidth // faster than multiplying
                }
                // get ready for next row...
                upperLeftX = 0
                upperLeftY += unitHeight // faster than multiplying
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////
    // Represents grid of colors
    private class Grid(rows: Int, columns: Int)
    {
        var grid: Array<Array<Color?>>
        var rows: Int
        var cols: Int

        init
        {
            grid = Array(rows) { arrayOfNulls(columns) }
            this.rows = rows
            this.cols = columns
            reset()
        }

        // Returns value in given grid element; null if row or column is out of range.
        private fun getElement(row: Int, column: Int): Color?
        {
            return if (row in 0..rows && column >= 0 && column <= cols) grid[row][column] else null
        }

        // Returns value in given grid element without doing any row/column index checking.
        // Is faster than getElement but will throw array index out of bounds exception if
        // parameter values are outside the bounds of the grid.
        fun getElementFast(row: Int, column: Int): Color?
        {
            return grid[row][column]
        }

        // Set the grid element.
        fun setElement(row: Int, column: Int, color: Int)
        {
            grid[row][column] = Color(color)
        }

        // Set the grid element.
        private fun setElement(row: Int, column: Int, color: Color)
        {
            grid[row][column] = color
        }

        // Just set all grid elements to black.
        fun reset()
        {
            for (i in 0 until rows)
            {
                for (j in 0 until cols)
                {
                    grid[i][j] = Color.BLACK
                }
            }
        }
    }

    companion object
    {
        private const val VERSION = "Version 1.0"
        private const val HEADING = "Bitmap Display++"

        /**
         * Main provided for pure stand-alone use.  Recommended stand-alone use is to write a driver program that
         * instantiates a Bitmap object then invokes its go() method. "stand-alone" means it is not invoked from the MARS
         * Tools menu.  "Pure" means there is no driver program to invoke the application.
         */
        @JvmStatic
        fun main(args: Array<String>)
        {
            BitmapDisplay("Bitmap Display stand-alone, " + VERSION, HEADING).go()
        }
    }
}
