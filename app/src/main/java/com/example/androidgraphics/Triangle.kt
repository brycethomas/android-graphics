package com.example.androidgraphics

import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

// number of coordinates per vertex in this array
const val COORDS_PER_VERTEX = 3
var triangleCoords = floatArrayOf(     // in counterclockwise order:
    0.0f, 0.622008459f, 0.0f,      // top
    -0.5f, -0.311004243f, 0.0f,    // bottom left
    0.5f, -0.311004243f, 0.0f      // bottom right
)
const val VALS_PER_COLOR = 4
var triangleColors = floatArrayOf(
    1.0f, 0.0f, 0.0f, 1.0f, // R
    0.0f, 1.0f, 0.1f, 1.0f, // G
    0.0f, 0.0f, 1.0f, 1.0f, // B
)

class Triangle {
    private var TAG: String = "Triangle"

    private var positionHandle: Int = 0
    private var mColorHandle: Int = 0
    private var outColorHandle: Int = 0
    private var glError: Int = 0

    private val vertexCount: Int = triangleCoords.size / COORDS_PER_VERTEX
    private val vertexStride: Int = COORDS_PER_VERTEX * 4 // 4 bytes per vertex

    // Use to access and set the view transformation
    private var vPMatrixHandle: Int = 0

    private var vertexBuffer: FloatBuffer =
        // (number of coordinate values * 4 bytes per float)
        ByteBuffer.allocateDirect(triangleCoords.size * 4).run {
            // use the device hardware's native byte order
            order(ByteOrder.nativeOrder())
            // create a floating point buffer from the ByteBuffer
            asFloatBuffer().apply {
                // add the coordinates to the FloatBuffer
                put(triangleCoords)
                // set the buffer to read the first coordinate
                position(0)
            }
        }

    private var colorBuffer: FloatBuffer =
        ByteBuffer.allocateDirect(triangleColors.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(triangleColors)
                position(0)
            }
        }

    private val vertexShaderCode =
    // This matrix member variable provides a hook to manipulate
        // the coordinates of the objects that use this vertex shader
        "uniform mat4 uMVPMatrix;" +
                "attribute vec4 vPosition;" +
                "attribute vec4 theColor;" +
                "varying vec4 outColor;" +
                "void main() {" +
                // the matrix must be included as a modifier of gl_Position
                // Note that the uMVPMatrix factor *must be first* in order
                // for the matrix multiplication product to be correct.
                "  gl_Position = uMVPMatrix * vPosition;" +
                "  outColor = theColor;" +
                "}"

    private val fragmentShaderCode =
        "precision mediump float;" +
                "uniform vec4 vColor;" +
                "varying vec4 outColor;" +
                "void main() {" +
                "  gl_FragColor = outColor;" +
                "}"

    private var mProgram: Int

    fun loadShader(type: Int, shaderCode: String): Int {
        // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
        // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
        return GLES20.glCreateShader(type).also { shader ->
            // add the source code to the shader and compile it
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    init {
        val vertexShader: Int = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader: Int = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        // create empty OpenGL ES Program
        mProgram = GLES20.glCreateProgram().also {
            // add the vertex shader to program
            GLES20.glAttachShader(it, vertexShader)
            // add the fragment shader to program
            GLES20.glAttachShader(it, fragmentShader)
            // creates OpenGL ES program executables
            GLES20.glLinkProgram(it)
        }
    }

    fun draw(mvpMatrix : FloatArray) { // pass in the calculated transformation matrix
        var err = drainGLErrors()
        if (err) return

        // Add program to OpenGL ES environment
        GLES20.glUseProgram(mProgram)

        err = drainGLErrors()
        if (err) return

        // get handle to vertex shader's vPosition member
        positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition")
        err = drainGLErrors()
        if (err) return

        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(positionHandle)
        // Prepare the triangle coordinate data
        GLES20.glVertexAttribPointer(
            positionHandle,
            COORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            vertexStride,
            vertexBuffer
        )

        outColorHandle = GLES20.glGetAttribLocation(mProgram, "theColor")
        err = drainGLErrors()
        if (err) return

        var bad = checkBadHandle(outColorHandle, "outColorHandle")
        if (bad) return

        GLES20.glEnableVertexAttribArray(outColorHandle)
        GLES20.glVertexAttribPointer(
            outColorHandle,
            VALS_PER_COLOR,
            GLES20.GL_FLOAT,
            false,
            0 /* tightly packed */,
            colorBuffer
        )

        // get handle to shape's transformation matrix
        vPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")

        // Pass the projection and view transformation to the shader
        GLES20.glUniformMatrix4fv(vPMatrixHandle, 1, false, mvpMatrix, 0)

        // Draw the triangle
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(mColorHandle)
    }

    fun drainGLErrors() : Boolean {
        var foundError = false
        glError = GLES20.glGetError();
        while (glError != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "Got GL Error: " + glError);
            foundError = true
            glError = GLES20.glGetError();
        }
        return foundError
    }

    fun checkBadHandle(handle : Int, handleName : String) : Boolean {
        if (handle == -1) {
            Log.e(TAG, handleName + " is a bad handle.")
            return true
        }
        return false
    }
}