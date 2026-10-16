package com.example.brewandbite.mypage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.brewandbite.common.applyStatusBarPadding
import com.example.brewandbite.databinding.FragmentMypageBinding

/** My Page 탭 (준비 중) */
class MyPageFragment : Fragment() {

    private var _binding: FragmentMypageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMypageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.mypageRoot.applyStatusBarPadding()

        // TODO: 화면 내용을 여기에 작성
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}