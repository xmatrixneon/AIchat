'use client'

import React, {
  createContext,
  useContext,
  useState,
  useEffect,
  ReactNode,
} from 'react'
import { useRouter } from 'next/navigation'
import { getCookie } from '@/utils/cookie'

type User = {
  name: string
  email: string
  // other user fields
}

type AuthContextType = {
  user: User | null
  loading: boolean
  error: string | null
  logout: () => void
  verifyToken: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter()
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  async function verifyToken() {
    setLoading(true)
    setError(null)

    try {
      const token = getCookie('token')
      if (!token) {
        setUser(null)
        router.push('/')
        setLoading(false)
        return
      }

      const res = await fetch('/api/verify', {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${token}`,
        },
      })

      if (!res.ok) {
        setUser(null)
        setError('Invalid or expired token')
        router.push('/')
      } else {
        const data = await res.json()
        setUser(data.user)
      }
    } catch (err) {
      setError('Network error during token verification')
      setUser(null)
      router.push('/')
    } finally {
      setLoading(false)
    }
  }

  function logout() {
    // Ideally call API to clear cookie
    setUser(null)
    router.push('/')
  }

  useEffect(() => {
    verifyToken()
  }, [])

  return (
    <AuthContext.Provider
      value={{
        user,
        loading,
        error,
        logout,
        verifyToken,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}
